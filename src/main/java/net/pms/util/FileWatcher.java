/*
 * This file is part of Universal Media Server, based on PS3 Media Server.
 *
 * This program is a free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; version 2 of the License only.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package net.pms.util;

import com.sun.jna.Platform;
import com.sun.nio.file.ExtendedWatchEventModifier;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.*;
import static java.nio.file.FileVisitOption.*;
import static java.nio.file.StandardWatchEventKinds.*;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.attribute.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstraction of the Java 7 nio WatchService api, which monitors native system
 * file-change notifications as opposed to directly polling or examining files.
 *
 * @see https://docs.oracle.com/javase/tutorial/essential/io/examples/WatchDir.java
 */
public class FileWatcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileWatcher.class);
	private static Notifier notifier = new Notifier("File event");
	private static WatchMap keys = new WatchMap();
	private static WatchService watchService = null;
	private static boolean running = false;

	/**
	 * This class should not be instantiated.
	 */
	private FileWatcher() {}

	/**
	 * Add a file watchpoint to the Watch Service. Will not
	 * add duplicates.
	 *
	 * @param w The watch object.
	 */
	public static void add(Watch w) {
		LOGGER.trace("FileWatcher: Adding " + w.getFileSpec());
		try {
			Path dir = Paths.get(FilenameUtils.getFullPath(w.getFileSpec()));
			LOGGER.trace("FileWatcher: path " + dir);
			w.init(dir);
			if (keys.contains(w)) {
				// Ignore duplicates
				return;
			}
			if (Watch.isRecursive(w)) {
				addRecursive(w, dir);
			} else {
				add(w, dir);
			}
		} catch (NullPointerException e) {
			LOGGER.info("Not watching invalid path {} for changes", w.getFileSpec());
		}
	}

	/**
	 * Adds a watch event to one directory only.
	 *
	 * @param w   the watch instance
	 * @param dir the directory to watch
	 */
	public static void add(Watch w, Path dir) {
		add(w, dir, false);
	}

	/**
	 * Adds a watch event that can be recursive using the FILE_TREE modifier.
	 * The modifier is only supported on Windows at the time of writing this.
	 *
	 * @param w               the watch instance
	 * @param dir             the directory to watch
	 * @param nativeRecursive whether to try making the watcher recursive
	 */
	public static void add(Watch w, Path dir, boolean nativeRecursive) {
		if (watchService == null) {
			start(dir);
		}

		// Ignore common system directories that should never be watched
		if (
			Platform.isMac() &&
			(
				dir.toString().contains("/Music/Audio Music Apps") ||
				dir.toString().contains("/Pictures/Photos Library.photoslibrary/resources/cpl/cloudsync.noindex/storage/filecache/") ||
				dir.toString().contains("/Pictures/Photos Library.photoslibrary/private") ||
				dir.toString().contains("/Pictures/Photos Library.photoslibrary/external")
			)
		) {
			return;
		}

		WatchKey key;

		try {
			Kind[] events = new Kind[] {ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE};
			if (nativeRecursive) {
				key = dir.register(watchService, events, ExtendedWatchEventModifier.FILE_TREE);
			} else {
				key = dir.register(watchService, events);
			}
			keys.put(key, w);
			LOGGER.debug("Added file watch at {}: {}", dir, w.getFileSpec());
		} catch (IOException e) {
			LOGGER.debug("Register error: " + e, e);
		}
	}

	/**
	 * Adds a recursive watcher to a directory.
	 *
	 * @param w   the watch instance
	 * @param dir the directory to watch
	 */
	public static void addRecursive(final Watch w, Path dir) {
		if (Platform.isWindows()) {
			add(w, dir, true);
		} else {
			try {
				Files.walkFileTree(dir, EnumSet.of(FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
						add(w, dir);
						return FileVisitResult.CONTINUE;
					}
				});
			} catch (IOException e) {
				LOGGER.debug("Recursion error: " + e, e);
			}
		}
	}

	/**
	 * Remove a file watchpoint from the Watch Service.
	 *
	 * @param w The watch object.
	 * @return remove done
	 */
	public static boolean remove(Watch w) {
		return keys.remove(w);
	}

	private static void start(Path dir) {
		// Start the service
		try {
			running = true;
			watchService = dir.getFileSystem().newWatchService();

			Runtime.getRuntime().addShutdownHook(new Thread("File watcher shutdown") {
				@Override
				public void run() {
					try {
						running = false;
						watchService.close();
						LOGGER.trace("Shut down file watcher");
					} catch (IOException e) {
						LOGGER.debug("Error while shutting down file watcher service: {}", e);
					}
				}
			});
		} catch (IOException e) {
			LOGGER.debug("Error creating WatchService: " + e, e);
		}

		// Watch for subscribed file events
		new Thread(() -> {
			try {
				do {
					// take() will block until events occur in our subscribed
					// directories
					WatchKey key = watchService.take();
					try {
						// Wait a bit in case there are a few repeats
						Thread.sleep(100);
					} catch (InterruptedException e) {
						LOGGER.debug("Sleep interrupted {}", e);
						Thread.currentThread().interrupt();
					}
					// Filter the received directory event(s)
					for (WatchEvent<?> e : key.pollEvents()) {
						final WatchEvent.Kind<?> kind = e.kind();
						if (kind != OVERFLOW) {
							WatchEvent<Path> event = (WatchEvent<Path>) e;
							// Determine the actual file
							Path path = (Path) key.watchable();
							final Path filename = path.resolve(event.context());
							final boolean isDir;
							if (!Files.exists(filename)) {
								isDir = FileUtil.isDirectory(filename.toString());
							} else {
								isDir = Files.isDirectory(filename/* , NOFOLLOW_LINKS */);
							}

							// See if we're watching for this specific file
							for (Iterator<Watch> iterator = keys.get(key).iterator(); iterator.hasNext();) {
								final Watch w = iterator.next();
								if (!Watch.isValid(w)) {
									LOGGER.debug("Deleting expired file watch at {}: {}", path, w.getFileSpec());
									iterator.remove();
									continue;
								}
								if (w.matcher.matches(filename)) {
									// We have an event of interest
									LOGGER.debug("{} (ct={}): {}", kind, event.count(), filename);
									if (isDir && kind == ENTRY_CREATE && Watch.isRecursive(w)) {
										// Traverse subdirs within new directory in a recursive scope
										addRecursive(w, filename);
									} else {
										// It's a regular event, schedule a notice
										notifier.schedule(
											new Notice(filename.toString(), kind.toString(), w, isDir),
											kind == ENTRY_MODIFY ? 500 : 0
										);
									}
								}
							}
						}
					}
					// Reset and clean up
					if (!key.reset()) {
						keys.remove(key);
					}
				} while (!keys.isEmpty());
			} catch (InterruptedException e) {
				//only log if running as InterruptedException will throw on shutdown
				if (running) {
					LOGGER.debug("Event process error: " + e, e);
				}
			}
		}, "File watcher").start();
	}

	public static interface Listener {
		/**
		 * A user-defined callback for receiving file change notifications.
		 *
		 * @param filename The changed filepath, relative or absolute depending on the original filespec.
		 * @param event The change itself: 'ENTRY_CREATE' 'ENTRY_MODIFY' or 'ENTRY_DELETE'.
		 * @param watch The original user-supplied watch object that triggered the match.
		 * @param isDir Whether the changed file is a directory.
		 */
		public void notify(String filename, String event, FileWatcher.Watch watch, boolean isDir);
	}

	/**
	 * A file watchpoint.
	 */
	public static class Watch {
		private final String fspec;
		private final int flag;
		private WeakReference<Listener> listener;
		private WeakReference<Object> item;
		private PathMatcher matcher;

		// Convenience constructors

		public Watch(String fspec, Listener listener) {
			this(fspec, listener, null, 0);
		}

		public Watch(String fspec, Listener listener, Object item) {
			this(fspec, listener, item, 0);
		}

		public Watch(String fspec, Listener listener, int flag) {
			this(fspec, listener, null, flag);
		}

		/**
		 * Creates a file watchpoint.
		 *
		 * @param fspec The filespec describing what files to match. There are 2 patterns:
		 *              - glob (default, forward slashes work in Windows too):
		 *                  foo/bar.jpg      - a specific file.
		 *                  foo/*            - any file in the foo directory.
		 *                  foo/bar/*.jpg    - any jpg in the foo/bar directory.
		 *                  foo/**.png       - any png in the foo directory or below.
		 *                  foo/*.{png,jpg}  - any png or jpg in the foo directory.
		 *              - regex (must be prefixed with 'regex:'):
		 *                  TODO - presumably any regex string.
		 *              For more on syntax see {@link java.nio.file.FileSystem#getPathMatcher(String)}.
		 * @param listener The user-defined callback.**
		 * @param item A user Object to attach to this watchpoint.**
		 * @param flag A user constant to attach to this watchpoint.
		 *
		 * @implNote ** Note that {@code listener} and {@code item} are held as weak references
		 *    and will not persist if anonymously inlined in the constructor call.
		 */
		public Watch(String fspec, Listener listener, Object item, int flag) {
			// Make sure we have double-backslashes in Windows paths
			this.fspec = fspec.replace("\\\\", "\\").replace("\\", "\\\\");
			this.listener = new WeakReference<>(listener);
			this.item = (item != null) ? new WeakReference<>(item) : null;
			this.flag = flag;
		}

		public void init(Path dir) {
			// Assume glob pattern if no prefix
			String match = (fspec.startsWith("glob:") || fspec.startsWith("regex:")) ? fspec : ("glob:" + fspec);
			matcher = dir.getFileSystem().getPathMatcher(match);
		}

		public String getFileSpec() {
			return fspec;
		}

		public boolean isFlag(int value) {
			return flag == value;
		}

		public Object getItem() {
			return (item != null) ? item.get() : null;
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Watch other) {
				return listener.get() == other.listener.get() &&
				(fspec != null && fspec.equals(other.fspec)) &&
				(item == other.item || (item != null && other.item != null && (item.get() == other.item.get() || item.get().equals(other.item.get())))) &&
				flag == other.flag;
			}
			return false;
		}

		@Override
		public int hashCode() {
			return fspec.hashCode() + listener.hashCode();
		}

		public static boolean isRecursive(Watch w) {
			// FIXME: How to detect recursion in 'regex:' syntax?
			return w.fspec.contains("**") && !w.fspec.startsWith("regex:");
		}

		public static boolean isValid(Watch w) {
			// Not valid if either the listener or item no longer exist
			return w.listener.get() != null || (w.item != null && w.item.get() == null);
		}
	}

	/**
	 * A map of file watchpoints by watchkey.
	 */
	static class WatchMap extends HashMap<WatchKey, ArrayList<Watch>> {
		private static final long serialVersionUID = 66052264663459389L;

		public void put(WatchKey k, Watch w) {
			if (!containsKey(k)) {
				put(k, new ArrayList<>());
			}
			get(k).add(w);
		}

		public boolean contains(Watch w) {
			for (ArrayList<Watch> a : values()) {
				if (a.contains(w)) {
					return true;
				}
			}
			return false;
		}

		public boolean remove(Watch w) {
			for (ArrayList<Watch> a : values()) {
				if (a.contains(w)) {
					return a.remove(w);
				}
			}
			return false;
		}
	}

	/**
	 * A runnable self-removing file event notice.
	 */
	static class Notice implements Runnable {
		String filename;
		String kind;
		Watch watch;
		boolean isDir;
		HashMap notifierQueue = null;

		public Notice(String filename, String kind, Watch watch, boolean isDir) {
			this.filename = filename;
			this.kind = kind;
			this.watch = watch;
			this.isDir = isDir;
		}

		@Override
		public void run() {
			watch.listener.get().notify(filename, kind, watch, isDir);
			notifierQueue.remove(this);
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Notice other) {
				return filename.equals(other.filename) && kind.equals(other.kind) && watch.equals(other.watch);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return (filename + kind).hashCode();
		}
	}

	/**
	 * A delayed file event notice scheduler.
	 */
	static class Notifier extends ScheduledThreadPoolExecutor {
		HashMap<Notice, ScheduledFuture<?>> queue = new HashMap<>();

		public Notifier(final String name) {
			super(5, (Runnable r) -> new Thread(r, name));
			setRemoveOnCancelPolicy(true);
		}

		/**
		 * Notices can be delayed slightly to allow ongoing file events to catch-up and cancel
		 * earlier in-progress notifications until the event is completed. This prevents
		 * sending 1000s of ENTRY_MODIFY notices during a file copy in linux, for instance.
		 *
		 * @param notice The notice.
		 * @param delay The delay in milliseconds.
		 */
		public void schedule(Notice notice, long delay) {
			// Put the notice in the queue
			notice.notifierQueue = queue;
			ScheduledFuture<?> superceded = queue.put(notice, schedule(notice, delay, TimeUnit.MILLISECONDS));
			// And cancel its previous instance, if any
			if (superceded != null) {
				superceded.cancel(false);
			}
		}
	}

}
