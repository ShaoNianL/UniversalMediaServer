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
package net.pms.database;

import com.google.gson.*;
import java.io.EOFException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import net.pms.dlna.DLNAThumbnail;
import net.pms.image.ImageFormat;
import net.pms.image.ImagesUtil.ScaleType;
import net.pms.util.APIUtils;
import net.pms.util.FileUtil;
import net.pms.util.UnknownFormatException;
import net.pms.util.UriFileRetriever;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MediaTableTVSeries extends MediaTable {
	private static final Logger LOGGER = LoggerFactory.getLogger(MediaTableTVSeries.class);
	public static final String TABLE_NAME = "TV_SERIES";
	/**
	 * COLUMNS
	 */
	public static final String COL_ID = "ID";
	private static final String COL_IMAGES = "IMAGES";
	private static final String COL_IMDBID = "IMDBID";
	private static final String COL_THUMBID = "THUMBID";
	private static final String COL_SIMPLIFIEDTITLE = "SIMPLIFIEDTITLE";
	private static final String COL_TITLE = "TITLE";
	private static final String BASIC_COLUMNS = "ENDYEAR, IMDBID, PLOT, SIMPLIFIEDTITLE, STARTYEAR, TITLE, TOTALSEASONS, VOTES, VERSION";
	private static final String BASIC_COLUMNS_PLACEHOLDERS = "?, ?, ?, ?, ?, ?, ?, ?, ?";
	/**
	 * The columns we added from TMDB in V11
	 */
	private static final String TMDB_COLUMNS = "CREATEDBY, CREDITS, EXTERNALIDS, FIRSTAIRDATE, HOMEPAGE, IMAGES, INPRODUCTION, LANGUAGES, LASTAIRDATE, NETWORKS, NUMBEROFEPISODES, NUMBEROFSEASONS, ORIGINCOUNTRY, ORIGINALLANGUAGE, ORIGINALTITLE, PRODUCTIONCOMPANIES, PRODUCTIONCOUNTRIES, SEASONS, SERIESTYPE, SPOKENLANGUAGES, STATUS, TAGLINE";
	private static final String TMDB_COLUMNS_PLACEHOLDERS = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?";

	/**
	 * COLUMNS with table name
	 */
	public static final String TABLE_COL_ID = TABLE_NAME + "." + COL_ID;
	public static final String TABLE_COL_IMDBID = TABLE_NAME + ".IMDBID";
	public static final String TABLE_COL_TITLE = TABLE_NAME + "." + COL_TITLE;
	public static final String TABLE_COL_SIMPLIFIEDTITLE = TABLE_NAME + "." + COL_SIMPLIFIEDTITLE;
	public static final String TABLE_COL_STARTYEAR = TABLE_NAME + ".STARTYEAR";
	public static final String TABLE_COL_IMAGES = TABLE_NAME + "." + COL_IMAGES;
	public static final String TABLE_COL_THUMBID = TABLE_NAME + "." + COL_THUMBID;

	private static final String SQL_LEFT_JOIN_TABLE_THUMBNAILS = "LEFT JOIN " + MediaTableThumbnails.TABLE_NAME + " ON " + TABLE_COL_THUMBID + " = " + MediaTableThumbnails.TABLE_COL_ID + " ";
	public static final String SQL_LEFT_JOIN_TABLE_VIDEO_METADATA_GENRES = "LEFT JOIN " + MediaTableVideoMetadataGenres.TABLE_NAME + " ON " + TABLE_COL_ID + " = " + MediaTableVideoMetadataGenres.TABLE_COL_TVSERIESID + " ";
	public static final String SQL_LEFT_JOIN_TABLE_VIDEO_METADATA_IMDB_RATING = "LEFT JOIN " + MediaTableVideoMetadataIMDbRating.TABLE_NAME + " ON " + TABLE_COL_ID + " = " + MediaTableVideoMetadataIMDbRating.TABLE_COL_TVSERIESID + " ";
	public static final String SQL_LEFT_JOIN_TABLE_VIDEO_METADATA_RATED = "LEFT JOIN " + MediaTableVideoMetadataRated.TABLE_NAME + " ON " + TABLE_COL_ID + " = " + MediaTableVideoMetadataRated.TABLE_COL_TVSERIESID + " ";

	private static final String SQL_GET_BY_IMDBID = "SELECT * FROM " + TABLE_NAME + " WHERE " + TABLE_COL_IMDBID + " = ? LIMIT 1";
	private static final String SQL_GET_BY_SIMPLIFIEDTITLE = "SELECT * FROM " + TABLE_NAME + " WHERE " + TABLE_COL_SIMPLIFIEDTITLE + " = ? LIMIT 1";
	private static final String SQL_GET_ID_BY_SIMPLIFIEDTITLE = "SELECT " + TABLE_COL_ID + " FROM " + TABLE_NAME + " WHERE " + TABLE_COL_SIMPLIFIEDTITLE + " = ? LIMIT 1";
	private static final String SQL_GET_TITLE_BY_IMDBID = "SELECT " + TABLE_COL_TITLE + " FROM " + TABLE_NAME + " WHERE " + TABLE_COL_IMDBID + " = ? LIMIT 1";
	private static final String SQL_GET_TITLE_BY_IMDBID_API_VERSION = "SELECT " + TABLE_COL_TITLE + " FROM " + TABLE_NAME + " WHERE " + TABLE_COL_IMDBID + " = ? AND VERSION = ? LIMIT 1";
	private static final String SQL_GET_IMAGES_BY_SIMPLIFIEDTITLE = "SELECT " + TABLE_COL_IMAGES + " FROM " + TABLE_NAME + " WHERE " + TABLE_COL_SIMPLIFIEDTITLE + " = ? LIMIT 1";
	private static final String SQL_GET_THUMBNAIL_BY_SIMPLIFIEDTITLE = "SELECT " + TABLE_COL_THUMBID + ", " + TABLE_COL_ID + ", " + MediaTableThumbnails.TABLE_COL_THUMBNAIL + " FROM " + TABLE_NAME + " " + SQL_LEFT_JOIN_TABLE_THUMBNAILS + " WHERE " + TABLE_COL_SIMPLIFIEDTITLE + " = ? LIMIT 1";
	private static final String SQL_GET_STARTYEAR_BY_SIMPLIFIEDTITLE = "SELECT " + TABLE_COL_STARTYEAR + " FROM " + TABLE_NAME + " WHERE " + TABLE_COL_SIMPLIFIEDTITLE + " = ? LIMIT 1";
	private static final String SQL_GET_TITLE_BY_SIMPLIFIEDTITLE = "SELECT " + TABLE_COL_TITLE + " FROM " + TABLE_NAME + " WHERE " + TABLE_COL_SIMPLIFIEDTITLE + " = ? LIMIT 1";
	private static final String SQL_GET_ISFULLYPLAYED = "SELECT " + MediaTableVideoMetadata.TABLE_COL_MOVIEORSHOWNAME + " FROM " + MediaTableFiles.TABLE_NAME + " " + MediaTableFiles.SQL_LEFT_JOIN_TABLE_FILES_STATUS + MediaTableFiles.SQL_LEFT_JOIN_TABLE_VIDEO_METADATA + "WHERE " + MediaTableFiles.TABLE_COL_FORMAT_TYPE + " = 4 AND " + MediaTableVideoMetadata.TABLE_COL_MOVIEORSHOWNAME + " = ? AND " + MediaTableVideoMetadata.TABLE_COL_ISTVEPISODE + " AND " + MediaTableFilesStatus.TABLE_COL_ISFULLYPLAYED + " IS NOT TRUE LIMIT 1";
	private static final String SQL_UPDATE_THUMBID = "UPDATE " + TABLE_NAME + " SET " + COL_THUMBID + " = ? WHERE " + TABLE_COL_ID + " = ?";
	private static final String SQL_UPDATE_IMDBID_NULL = "UPDATE " + TABLE_NAME + " SET " + COL_IMDBID + " = null WHERE " + TABLE_COL_ID + " = ?";
	private static final String SQL_INSERT_TITLE = "INSERT INTO " + TABLE_NAME + " (" + COL_SIMPLIFIEDTITLE + ", " + COL_TITLE + ") VALUES (?, ?)";
	private static final String SQL_INSERT_ALL = "INSERT INTO " + TABLE_NAME + " (" + BASIC_COLUMNS + ", " + TMDB_COLUMNS + ") VALUES (" + BASIC_COLUMNS_PLACEHOLDERS + ", " + TMDB_COLUMNS_PLACEHOLDERS + ")";
	private static final String SQL_DELETE_IMDBID = "DELETE FROM " + TABLE_NAME + " WHERE " + TABLE_COL_IMDBID + " = ?";

	/**
	 * Used by child tables
	 */
	public static final String CHILD_ID = "TVSERIESID";

	/**
	 * Table version must be increased every time a change is done to the table
	 * definition. Table upgrade SQL must also be added to
	 * {@link #upgradeTable(Connection, int)}
	 */
	private static final int TABLE_VERSION = 7;


	private static final Gson GSON = new Gson();

	private static final UriFileRetriever URI_FILE_RETRIEVER = new UriFileRetriever();

	/**
	 * Checks and creates or upgrades the table as needed.
	 *
	 * @param connection the {@link Connection} to use
	 *
	 * @throws SQLException
	 */
	protected static void checkTable(final Connection connection) throws SQLException {
		if (tableExists(connection, TABLE_NAME)) {
			Integer version = MediaTableTablesVersions.getTableVersion(connection, TABLE_NAME);
			if (version != null) {
				if (version < TABLE_VERSION) {
					upgradeTable(connection, version);
				} else if (version > TABLE_VERSION) {
					LOGGER.warn(LOG_TABLE_NEWER_VERSION_DELETEDB, DATABASE_NAME, TABLE_NAME, DATABASE.getDatabaseFilename());
				}
			} else {
				LOGGER.warn(LOG_TABLE_UNKNOWN_VERSION_RECREATE, DATABASE_NAME, TABLE_NAME);
				dropTable(connection, TABLE_NAME);
				createTable(connection);
				MediaTableTablesVersions.setTableVersion(connection, TABLE_NAME, TABLE_VERSION);
			}
		} else {
			createTable(connection);
			MediaTableTablesVersions.setTableVersion(connection, TABLE_NAME, TABLE_VERSION);
		}
	}

	/**
	 * This method <strong>MUST</strong> be updated if the table definition are
	 * altered. The changes for each version in the form of
	 * <code>ALTER TABLE</code> must be implemented here.
	 *
	 * @param connection the {@link Connection} to use
	 * @param currentVersion the version to upgrade <strong>from</strong>
	 *
	 * @throws SQLException
	 */
	private static void upgradeTable(final Connection connection, final int currentVersion) throws SQLException {
		LOGGER.info(LOG_UPGRADING_TABLE, DATABASE_NAME, TABLE_NAME, currentVersion, TABLE_VERSION);
		for (int version = currentVersion; version < TABLE_VERSION; version++) {
			LOGGER.trace(LOG_UPGRADING_TABLE, DATABASE_NAME, TABLE_NAME, version, version + 1);
			switch (version) {
				case 1:
					try (Statement statement = connection.createStatement()) {
						if (!isColumnExist(connection, TABLE_NAME, "VERSION")) {
							statement.execute("ALTER TABLE " + TABLE_NAME + " ADD VERSION VARCHAR");
							statement.execute("CREATE INDEX IMDBID_VERSION ON " + TABLE_NAME + "(IMDBID, VERSION)");
						}
					} catch (SQLException e) {
						LOGGER.error("Failed upgrading database table {} for {}", TABLE_NAME, e.getMessage());
						LOGGER.error("Please use the 'Reset the cache' button on the 'Navigation Settings' tab, close UMS and start it again.");
						throw new SQLException(e);
					}
					break;
				case 2:
					if (isColumnExist(connection, TABLE_NAME, "YEAR")) {
						LOGGER.trace("Renaming column name YEAR to MEDIA_YEAR");
						executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ALTER COLUMN `YEAR` RENAME TO MEDIA_YEAR");
					}
					break;
				case 3:
					LOGGER.trace("Adding TMDB columns");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS CREATEDBY VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS CREDITS VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS EXTERNALIDS VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS FIRSTAIRDATE VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS HOMEPAGE VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS IMAGES VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS INPRODUCTION VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS LANGUAGES VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS LASTAIRDATE VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS NETWORKS VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS NUMBEROFEPISODES DOUBLE PRECISION");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS NUMBEROFSEASONS DOUBLE PRECISION");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS ORIGINCOUNTRY VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS ORIGINALLANGUAGE VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS ORIGINALTITLE VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS PRODUCTIONCOMPANIES VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS PRODUCTIONCOUNTRIES VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS SEASONS VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS SERIESTYPE VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS SPOKENLANGUAGES VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS STATUS VARCHAR");
					executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN IF NOT EXISTS TAGLINE VARCHAR");
					break;
				case 4:
					// This version was for testing, left here to not break tester dbs
					break;
				case 5:
					if (isColumnExist(connection, TABLE_NAME, "INPRODUCTION")) {
						executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " DROP COLUMN INPRODUCTION");
						executeUpdate(connection, "ALTER TABLE " + TABLE_NAME + " ADD COLUMN INPRODUCTION BOOLEAN");
					}
					break;
				case 6:
					executeUpdate(connection, "CREATE INDEX IF NOT EXISTS " + TABLE_NAME + "_" + COL_THUMBID + "_IDX ON " + TABLE_NAME + "(" + COL_THUMBID + ")");

					//set old json datas to be rescanned
					if (isColumnExist(connection, TABLE_NAME, "VERSION")) {
						String[] badJsonColumns = {"LANGUAGES", "ORIGINCOUNTRY"};
						for (String badJsonColumn : badJsonColumns) {
							if (isColumnExist(connection, TABLE_NAME, badJsonColumn)) {
								executeUpdate(connection, "UPDATE " + TABLE_NAME + " SET " + COL_IMDBID + " = NULL WHERE RIGHT(" + badJsonColumn + ", 1) = ','");
							}
						}
					}
					break;
				default:
					throw new IllegalStateException(
						getMessage(LOG_UPGRADING_TABLE_MISSING, DATABASE_NAME, TABLE_NAME, version, TABLE_VERSION)
					);
			}
		}

		try {
			MediaTableTablesVersions.setTableVersion(connection, TABLE_NAME, TABLE_VERSION);
		} catch (SQLException e) {
			LOGGER.error("Failed setting the table version of the {} for {}", TABLE_NAME, e.getMessage());
			throw new SQLException(e);
		}
	}

	private static void createTable(final Connection connection) throws SQLException {
		LOGGER.debug(LOG_CREATING_TABLE, DATABASE_NAME, TABLE_NAME);
		execute(connection,
			"CREATE TABLE " + TABLE_NAME + "(" +
				"ID                   IDENTITY           PRIMARY KEY , " +
				"ENDYEAR              VARCHAR(1024)                  , " +
				"IMDBID               VARCHAR(1024)                  , " +
				"THUMBID              BIGINT                         , " +
				"PLOT                 VARCHAR(20000)                 , " +
				"STARTYEAR            VARCHAR(1024)                  , " +
				"TITLE                VARCHAR(1024)      NOT NULL    , " +
				"SIMPLIFIEDTITLE      VARCHAR(1024)      NOT NULL    , " +
				"TOTALSEASONS         DOUBLE PRECISION               , " +
				"VERSION              VARCHAR(1024)                  , " +
				"VOTES                VARCHAR(1024)                  , " +
				"CREATEDBY            VARCHAR                        , " +
				"CREDITS              VARCHAR                        , " +
				"EXTERNALIDS          VARCHAR                        , " +
				"FIRSTAIRDATE         VARCHAR                        , " +
				"HOMEPAGE             VARCHAR                        , " +
				"IMAGES               VARCHAR                        , " +
				"INPRODUCTION         BOOLEAN                        , " +
				"LANGUAGES            VARCHAR                        , " +
				"LASTAIRDATE          VARCHAR                        , " +
				"NETWORKS             VARCHAR                        , " +
				"NUMBEROFEPISODES     DOUBLE PRECISION               , " +
				"NUMBEROFSEASONS      DOUBLE PRECISION               , " +
				"ORIGINCOUNTRY        VARCHAR                        , " +
				"ORIGINALLANGUAGE     VARCHAR                        , " +
				"ORIGINALTITLE        VARCHAR                        , " +
				"PRODUCTIONCOMPANIES  VARCHAR                        , " +
				"PRODUCTIONCOUNTRIES  VARCHAR                        , " +
				"SEASONS              VARCHAR                        , " +
				"SERIESTYPE           VARCHAR                        , " +
				"SPOKENLANGUAGES      VARCHAR                        , " +
				"STATUS               VARCHAR                        , " +
				"TAGLINE              VARCHAR                          " +
			")",
			"CREATE INDEX IMDBID_IDX ON " + TABLE_NAME + "(IMDBID)",
			"CREATE INDEX TITLE_IDX ON " + TABLE_NAME + "(TITLE)",
			"CREATE INDEX SIMPLIFIEDTITLE_IDX ON " + TABLE_NAME + "(SIMPLIFIEDTITLE)",
			"CREATE INDEX IMDBID_VERSION ON " + TABLE_NAME + "(IMDBID, VERSION)",
			"CREATE INDEX " + TABLE_NAME + "_" + COL_THUMBID + "_IDX ON " + TABLE_NAME + "(" + COL_THUMBID + ")"
		);
	}

	/**
	 * Sets a new entry and returns the row ID.
	 *
	 * @param connection the db connection
	 * @param tvSeries data about this series from the API
	 * @param seriesName the name of the series, for when we don't have API data yet
	 * @return the new row ID
	 */
	public static Long set(final Connection connection, final JsonObject tvSeries, final String seriesName) {
		boolean trace = LOGGER.isTraceEnabled();
		String sql;
		String condition;
		String simplifiedTitle;

		if (seriesName != null) {
			simplifiedTitle = FileUtil.getSimplifiedShowName(seriesName);
			condition = simplifiedTitle;
			sql = SQL_GET_BY_SIMPLIFIEDTITLE;
		} else {
			String title = APIUtils.getStringOrNull(tvSeries, "title");
			if (StringUtils.isNotBlank(title)) {
				simplifiedTitle = FileUtil.getSimplifiedShowName(title);
				condition = APIUtils.getStringOrNull(tvSeries, "imdbID");
				sql = SQL_GET_BY_IMDBID;
			} else {
				LOGGER.debug("Attempted to set TV series info with no series title: {}", (tvSeries != null ? tvSeries.toString() : "Nothing provided"));
				return null;
			}
		}

		try {
			try (PreparedStatement selectStatement = connection.prepareStatement(sql, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
				selectStatement.setString(1, condition);
				try (ResultSet result = selectStatement.executeQuery()) {
					if (result.next()) {
						if (trace) {
							LOGGER.trace("Found entry in " + TABLE_NAME);
						}
						return result.getLong("ID");
					} else {
						if (trace) {
							LOGGER.trace("Entry \"{}\" not found in " + TABLE_NAME + ", inserting", simplifiedTitle);
						}

						String insertQuery;
						if (seriesName != null) {
							insertQuery = SQL_INSERT_TITLE;
						} else {
							insertQuery = SQL_INSERT_ALL;
						}
						try (PreparedStatement insertStatement = connection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
							if (seriesName != null) {
								insertStatement.setString(1, simplifiedTitle);
								insertStatement.setString(2, seriesName);
							} else {
								insertStatement.setString(1, APIUtils.getStringOrNull(tvSeries, "endYear"));
								insertStatement.setString(2, APIUtils.getStringOrNull(tvSeries, "imdbID"));
								insertStatement.setString(3, APIUtils.getStringOrNull(tvSeries, "plot"));
								insertStatement.setString(4, simplifiedTitle);
								insertStatement.setString(5, APIUtils.getStringOrNull(tvSeries, "startYear"));
								insertStatement.setString(6, APIUtils.getStringOrNull(tvSeries, "title"));

								if (tvSeries.has("totalSeasons")) {
									insertStatement.setDouble(7, tvSeries.get("totalSeasons").getAsDouble());
								} else {
									insertStatement.setDouble(7, 0.0);
								}

								insertStatement.setString(8, APIUtils.getStringOrNull(tvSeries, "votes"));
								insertStatement.setString(9, APIUtils.getApiDataSeriesVersion());

								// TMDB data, since v11
								if (tvSeries.has("createdBy")) {
									insertStatement.setString(10, tvSeries.get("createdBy").toString());
								}
								if (tvSeries.has("credits")) {
									insertStatement.setString(11, tvSeries.get("credits").toString());
								}
								if (tvSeries.has("externalIDs")) {
									insertStatement.setString(12, tvSeries.get("externalIDs").toString());
								}
								insertStatement.setString(13, APIUtils.getStringOrNull(tvSeries, "firstAirDate"));
								insertStatement.setString(14, APIUtils.getStringOrNull(tvSeries, "homepage"));
								if (tvSeries.has("images")) {
									insertStatement.setString(15, tvSeries.get("images").toString());
								}
								if (tvSeries.has("inProduction")) {
									insertStatement.setBoolean(16, tvSeries.get("inProduction").getAsBoolean());
								} else {
									insertStatement.setBoolean(16, false);
								}
								if (tvSeries.has("languages")) {
									insertStatement.setString(17, tvSeries.get("languages").toString());
								}
								insertStatement.setString(18, APIUtils.getStringOrNull(tvSeries, "lastAirDate"));
								if (tvSeries.has("networks")) {
									insertStatement.setString(19, tvSeries.get("networks").toString());
								}
								if (tvSeries.has("numberOfEpisodes")) {
									insertStatement.setDouble(20, tvSeries.get("numberOfEpisodes").getAsDouble());
								} else {
									insertStatement.setNull(20, Types.DOUBLE);
								}
								if (tvSeries.has("numberOfSeasons")) {
									insertStatement.setDouble(21, tvSeries.get("numberOfSeasons").getAsDouble());
								} else {
									insertStatement.setNull(21, Types.DOUBLE);
								}
								if (tvSeries.has("originCountry")) {
									insertStatement.setString(22, tvSeries.get("originCountry").toString());
								}
								insertStatement.setString(23, APIUtils.getStringOrNull(tvSeries, "originalLanguage"));
								insertStatement.setString(24, APIUtils.getStringOrNull(tvSeries, "originalTitle"));
								if (tvSeries.has("productionCompanies")) {
									insertStatement.setString(25, tvSeries.get("productionCompanies").toString());
								}
								if (tvSeries.has("productionCountries")) {
									insertStatement.setString(26, tvSeries.get("productionCountries").toString());
								}
								if (tvSeries.has("seasons")) {
									insertStatement.setString(27, tvSeries.get("seasons").toString());
								}
								insertStatement.setString(28, APIUtils.getStringOrNull(tvSeries, "seriesType"));
								if (tvSeries.has("spokenLanguages")) {
									insertStatement.setString(29, tvSeries.get("spokenLanguages").toString());
								}
								insertStatement.setString(30, APIUtils.getStringOrNull(tvSeries, "status"));
								insertStatement.setString(31, APIUtils.getStringOrNull(tvSeries, "tagline"));
							}
							insertStatement.executeUpdate();

							try (ResultSet generatedKeys = insertStatement.getGeneratedKeys()) {
								if (generatedKeys.next()) {
									return generatedKeys.getLong(1);
								} else {
									LOGGER.debug("Generated key not returned in " + TABLE_NAME);
								}
							}
						}
					}
				}
			}
		} catch (SQLException e) {
			LOGGER.error(LOG_ERROR_WHILE_IN_FOR, DATABASE_NAME, "writing", TABLE_NAME, "tv Series", e.getMessage());
			LOGGER.trace("", e);
		}

		return null;
	}

	/**
	 * Get TV series title by IMDb ID.
	 * If we have the latest version number from the
	 * API, narrow the result to that version.
	 * @param connection the db connection
	 * @param imdbID
	 * @return the title or null
	 */
	public static String getTitleByIMDbID(final Connection connection, final String imdbID) {
		String sql;
		String latestVersion = null;
		if (CONFIGURATION.getExternalNetwork()) {
			latestVersion = APIUtils.getApiDataSeriesVersion();
		}
		if (latestVersion != null) {
			sql = SQL_GET_TITLE_BY_IMDBID_API_VERSION;
		} else {
			sql = SQL_GET_TITLE_BY_IMDBID;
		}

		try {
			try (PreparedStatement statement = connection.prepareStatement(sql)) {
				statement.setString(1, imdbID);
				if (latestVersion != null) {
					statement.setString(2, latestVersion);
				}
				try (ResultSet resultSet = statement.executeQuery()) {
					if (resultSet.next()) {
						return resultSet.getString(COL_TITLE);
					} else {
						LOGGER.trace("Did not find title by IMDb ID using query: {}", statement.toString());
					}
				}
			}
		} catch (SQLException e) {
			LOGGER.error(LOG_ERROR_WHILE_VAR_IN, DATABASE_NAME, "reading tv series from imdbID", imdbID, TABLE_NAME, e.getMessage());
			LOGGER.trace("", e);
		}

		return null;
	}

	/**
	 * Returns the images based on title.
	 *
	 * @param connection the db connection
	 * @param title
	 * @return
	 */
	public static String getImagesByTitle(final Connection connection, final String title) {
		String simplifiedTitle = FileUtil.getSimplifiedShowName(title);
		try {
			try (PreparedStatement statement = connection.prepareStatement(SQL_GET_IMAGES_BY_SIMPLIFIEDTITLE)) {
				statement.setString(1, simplifiedTitle);
				try (ResultSet resultSet = statement.executeQuery()) {
					if (resultSet.next()) {
						return resultSet.getString(COL_IMAGES);
					}
				}
			}
		} catch (SQLException e) {
			LOGGER.error(LOG_ERROR_WHILE_VAR_IN, DATABASE_NAME, "reading images from title", title, TABLE_NAME, e.getMessage());
			LOGGER.trace("", e);
		}

		return null;
	}

	/**
	 * Returns a row id based on title.
	 *
	 * @param connection the db connection
	 * @param title
	 * @return
	 */
	public static Long getIdByTitle(final Connection connection, final String title) {
		String simplifiedTitle = FileUtil.getSimplifiedShowName(title);
		try {
			try (PreparedStatement statement = connection.prepareStatement(SQL_GET_ID_BY_SIMPLIFIEDTITLE)) {
				statement.setString(1, simplifiedTitle);
				try (ResultSet resultSet = statement.executeQuery()) {
					if (resultSet.next()) {
						return resultSet.getLong(COL_ID);
					}
				}
			}
		} catch (SQLException e) {
			LOGGER.error(LOG_ERROR_WHILE_VAR_IN, DATABASE_NAME, "reading id from title", title, TABLE_NAME, e.getMessage());
			LOGGER.trace("", e);
		}

		return null;
	}

	/**
	 * @param connection the db connection
	 * @param title
	 * @return a thumbnail based on title.
	 */
	public static DLNAThumbnail getThumbnailByTitle(final Connection connection, final String title) {
		String simplifiedTitle = FileUtil.getSimplifiedShowName(title);
		Integer thumbnailId = null;
		Integer tvSeriesId = null;

		try (PreparedStatement statement = connection.prepareStatement(SQL_GET_THUMBNAIL_BY_SIMPLIFIEDTITLE)) {
			statement.setString(1, simplifiedTitle);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					thumbnailId = resultSet.getInt("THUMBID");
					tvSeriesId = resultSet.getInt("ID");
					return (DLNAThumbnail) resultSet.getObject("THUMBNAIL");
				}
			}
		} catch (Exception e) {
			LOGGER.debug("Cached thumbnail for TV series {} seems to be from a previous version, regenerating", title);
			LOGGER.trace("", e);

			// Regenerate the thumbnail from a stored poster if it exists
			Object[] posterInfo = MediaTableVideoMetadataPosters.getByTVSeriesName(connection, title);
			if (posterInfo == null) {
				// this should never happen, since the only way to have a TV series thumbnail is from an API poster
				LOGGER.debug("No poster URI was found locally for {}, removing API information for TV series", title);
				if (thumbnailId != null) {
					MediaTableThumbnails.removeById(connection, thumbnailId);
					removeImdbIdById(connection, tvSeriesId);
				}
				return null;
			}

			String posterURL = (String) posterInfo[0];
			Long tvSeriesDatabaseId = (Long) posterInfo[1];
			try {
				byte[] image = URI_FILE_RETRIEVER.get(posterURL);
				DLNAThumbnail thumbnail = (DLNAThumbnail) DLNAThumbnail.toThumbnail(image, 640, 480, ScaleType.MAX, ImageFormat.JPEG, false);
				MediaTableThumbnails.setThumbnail(connection, thumbnail, null, tvSeriesDatabaseId, true);
				return thumbnail;
			} catch (EOFException e2) {
				LOGGER.debug(
					"Error reading \"{}\" thumbnail from posters table: Unexpected end of stream, probably corrupt or read error.",
					posterURL
				);
			} catch (UnknownFormatException e2) {
				LOGGER.debug("Could not read \"{}\" thumbnail from posters table: {}", posterURL, e2.getMessage());
			} catch (IOException e2) {
				LOGGER.error("Error reading \"{}\" thumbnail from posters table: {}", posterURL, e2.getMessage());
				LOGGER.trace("", e2);
			}
		}

		return null;
	}

	public static String getStartYearBySimplifiedTitle(final Connection connection, final String simplifiedTitle) {
		try (PreparedStatement statement = connection.prepareStatement(SQL_GET_STARTYEAR_BY_SIMPLIFIEDTITLE)) {
			statement.setString(1, simplifiedTitle);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					return resultSet.getString("STARTYEAR");
				}
			}
		} catch (SQLException ex) {
			LOGGER.error(
				LOG_ERROR_WHILE_IN_FOR,
				DATABASE_NAME,
				"reading",
				TABLE_NAME,
				simplifiedTitle,
				ex.getMessage()
			);
			LOGGER.trace("", ex);
		}
		return null;
	}

	public static void updateThumbnailId(final Connection connection, long id, int thumbId) {
		try {
			try (
				PreparedStatement ps = connection.prepareStatement(SQL_UPDATE_THUMBID);
			) {
				ps.setInt(1, thumbId);
				ps.setLong(2, id);
				ps.executeUpdate();
				LOGGER.trace("TV series THUMBID updated to {} for {}", thumbId, id);
			}
		} catch (SQLException e) {
			LOGGER.error(LOG_ERROR_WHILE_IN_FOR, DATABASE_NAME, "updating cached thumbnail", TABLE_NAME, id, e.getMessage());
			LOGGER.trace("", e);
		}
	}

	/**
	 * @param connection the db connection
	 * @param simplifiedTitle
	 * @return all data across all tables for a video file, if it has an IMDb ID stored.
	 */

	public static JsonObject getTvSerieMetadataAsJsonObject(final Connection connection, final String simplifiedTitle) {
		if (connection == null || simplifiedTitle == null) {
			return null;
		}
		boolean trace = LOGGER.isTraceEnabled();

		try {
			String sql = "SELECT * FROM " + TABLE_NAME + " WHERE " + TABLE_COL_SIMPLIFIEDTITLE + " = ? LIMIT 1";
			try (PreparedStatement selectStatement = connection.prepareStatement(sql)) {
				selectStatement.setString(1, simplifiedTitle);
				if (trace) {
					LOGGER.trace("Searching " + TABLE_NAME + " with \"{}\"", selectStatement);
				}
				try (ResultSet rs = selectStatement.executeQuery()) {
					if (rs.next()) {
						JsonObject result = new JsonObject();
						Long id = rs.getLong("ID");
						result.addProperty("imdbID", rs.getString(COL_IMDBID));
						result.addProperty("plot", rs.getString("PLOT"));
						result.addProperty("startYear", rs.getString("STARTYEAR"));
						result.addProperty("title", rs.getString("TITLE"));
						result.addProperty("totalSeasons", rs.getDouble("TOTALSEASONS"));
						result.addProperty("createdBy", rs.getString("CREATEDBY"));
						addJsonElementToJsonObjectIfExists(result, "credits", rs.getString("CREDITS"));
						addJsonElementToJsonObjectIfExists(result, "externalIDs", rs.getString("EXTERNALIDS"));
						result.addProperty("firstAirDate", rs.getString("FIRSTAIRDATE"));
						result.addProperty("homepage", rs.getString("HOMEPAGE"));
						addJsonElementToJsonObjectIfExists(result, "images", rs.getString("IMAGES"));
						result.addProperty("inProduction", rs.getBoolean("INPRODUCTION"));
						result.addProperty("homepage", rs.getString("HOMEPAGE"));
						addJsonElementToJsonObjectIfExists(result, "languages", rs.getString("LANGUAGES"));
						result.addProperty("lastAirDate", rs.getString("LASTAIRDATE"));
						addJsonElementToJsonObjectIfExists(result, "networks", rs.getString("NETWORKS"));
						result.addProperty("numberOfEpisodes", rs.getDouble("NUMBEROFEPISODES"));
						result.addProperty("numberOfSeasons", rs.getDouble("NUMBEROFSEASONS"));
						result.addProperty("originCountry", rs.getString("ORIGINCOUNTRY"));
						result.addProperty("originalLanguage", rs.getString("ORIGINALLANGUAGE"));
						result.addProperty("originalTitle", rs.getString("ORIGINALTITLE"));
						addJsonElementToJsonObjectIfExists(result, "productionCompanies", rs.getString("PRODUCTIONCOMPANIES"));
						addJsonElementToJsonObjectIfExists(result, "productionCountries", rs.getString("PRODUCTIONCOUNTRIES"));
						addJsonElementToJsonObjectIfExists(result, "seasons", rs.getString("SEASONS"));
						result.addProperty("seriesType", rs.getString("SERIESTYPE"));
						addJsonElementToJsonObjectIfExists(result, "spokenLanguages", rs.getString("SPOKENLANGUAGES"));
						result.addProperty("status", rs.getString("STATUS"));
						result.addProperty("tagline", rs.getString("TAGLINE"));
						result.add("actors", MediaTableVideoMetadataActors.getJsonArrayForTvSerie(connection, id));
						result.addProperty("award", MediaTableVideoMetadataAwards.getValueForTvSerie(connection, id));
						result.add("countries", MediaTableVideoMetadataCountries.getJsonArrayForTvSerie(connection, id));
						result.add("directors", MediaTableVideoMetadataDirectors.getJsonArrayForTvSerie(connection, id));
						result.add("genres", MediaTableVideoMetadataGenres.getJsonArrayForTvSerie(connection, id));
						result.addProperty("imdbRating", MediaTableVideoMetadataIMDbRating.getValueForTvSerie(connection, id));
						result.addProperty("poster", MediaTableVideoMetadataPosters.getValueForTvSerie(connection, id));
						result.addProperty("production", MediaTableVideoMetadataProduction.getValueForTvSerie(connection, id));
						result.addProperty("rated", MediaTableVideoMetadataRated.getValueForTvSerie(connection, id));
						result.add("ratings", MediaTableVideoMetadataRatings.getJsonArrayForTvSerie(connection, id));
						result.addProperty("released", MediaTableVideoMetadataReleased.getValueForTvSerie(connection, id));
						return result;
					}
				}
			}
		} catch (SQLException e) {
			LOGGER.error(LOG_ERROR_WHILE_IN_FOR, DATABASE_NAME, "reading API results", TABLE_NAME, simplifiedTitle, e.getMessage());
			LOGGER.debug("", e);
		}

		return null;
	}

	private static void addJsonElementToJsonObjectIfExists(final JsonObject dest, final String property, final String jsonString) {
		if (StringUtils.isEmpty(jsonString)) {
			return;
		}
		try {
			JsonElement element = GSON.fromJson(jsonString, JsonElement.class);
			dest.add(property, element);
		} catch (JsonSyntaxException e) {
		}
	}

	/**
	 * Returns a similar TV series name from the database.
	 *
	 * @param connection the db connection
	 * @param title
	 * @return
	 */
	public static String getSimilarTVSeriesName(final Connection connection, String title) {
		if (title == null) {
			return title;
		}

		String simplifiedTitle = FileUtil.getSimplifiedShowName(title);
		try (PreparedStatement statement = connection.prepareStatement(SQL_GET_TITLE_BY_SIMPLIFIEDTITLE)) {
			statement.setString(1, simplifiedTitle);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					String str = resultSet.getString(1);
					return StringUtils.isBlank(str) ? MediaTableFiles.NONAME : str;
				}
			}
		} catch (SQLException e) {
			LOGGER.error(LOG_ERROR_WHILE_IN_FOR, DATABASE_NAME, "reading", TABLE_NAME, "SimilarTVSeriesName", e.getMessage());
			LOGGER.trace("", e);
		}

		return null;
	}

	/**
	 * Updates an existing row with information from our API.
	 *
	 * @param connection the db connection
	 * @param tvSeries
	 */
	public static void insertAPIMetadata(final Connection connection, final JsonObject tvSeries) {
		if (tvSeries == null) {
			LOGGER.warn("Couldn't write API data for \"{}\" to the database because there is no media information");
			return;
		}
		String title = APIUtils.getStringOrNull(tvSeries, "title");
		String simplifiedTitle = FileUtil.getSimplifiedShowName(title);

		try (
			PreparedStatement ps = connection.prepareStatement(
				SQL_GET_BY_SIMPLIFIEDTITLE,
				ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_UPDATABLE
			)
		) {
			ps.setString(1, simplifiedTitle);
			LOGGER.trace("Inserting API metadata for " + simplifiedTitle + ": " + tvSeries.toString());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					String json;
					rs.updateString("ENDYEAR", APIUtils.getStringOrNull(tvSeries, "endYear"));
					rs.updateString("IMDBID", APIUtils.getStringOrNull(tvSeries, "imdbID"));
					rs.updateString("PLOT", APIUtils.getStringOrNull(tvSeries, "plot"));
					rs.updateString("STARTYEAR", APIUtils.getStringOrNull(tvSeries, "startYear"));
					rs.updateString("TITLE", title);
					if (tvSeries.get("totalSeasons") != null) {
						rs.updateDouble("TOTALSEASONS", tvSeries.get("totalSeasons").getAsDouble());
					}
					rs.updateString("VERSION", APIUtils.getApiDataSeriesVersion());
					rs.updateString("VOTES", APIUtils.getStringOrNull(tvSeries, "votes"));

					// TMDB columns added in V11
					if (tvSeries.get("createdBy") != null) {
						json = GSON.toJson(tvSeries.get("createdBy"));
						rs.updateString("CREATEDBY", json);
					}
					if (tvSeries.get("credits") != null) {
						json = GSON.toJson(tvSeries.get("credits"));
						rs.updateString("CREDITS", json);
					}
					if (tvSeries.get("externalIDs") != null) {
						json = GSON.toJson(tvSeries.get("externalIDs"));
						rs.updateString("EXTERNALIDS", json);
					}
					rs.updateString("FIRSTAIRDATE", APIUtils.getStringOrNull(tvSeries, "firstAirDate"));
					rs.updateString("HOMEPAGE", APIUtils.getStringOrNull(tvSeries, "homepage"));
					if (tvSeries.has("images")) {
						rs.updateString("IMAGES", tvSeries.get("images").toString());
					}
					if (tvSeries.has("inProduction")) {
						rs.updateBoolean("INPRODUCTION", tvSeries.get("inProduction").getAsBoolean());
					}
					if (tvSeries.has("languages")) {
						rs.updateString("LANGUAGES", tvSeries.get("languages").toString());
					}
					rs.updateString("LASTAIRDATE", APIUtils.getStringOrNull(tvSeries, "lastAirDate"));
					if (tvSeries.has("networks")) {
						rs.updateString("NETWORKS", tvSeries.get("networks").toString());
					}
					if (tvSeries.has("numberOfEpisodes")) {
						rs.updateDouble("NUMBEROFEPISODES", tvSeries.get("numberOfEpisodes").getAsDouble());
					}
					if (tvSeries.has("numberOfSeasons")) {
						rs.updateDouble("NUMBEROFSEASONS", tvSeries.get("numberOfSeasons").getAsDouble());
					}
					if (tvSeries.has("originCountry")) {
						rs.updateString("ORIGINCOUNTRY", tvSeries.get("originCountry").toString());
					}
					rs.updateString("ORIGINALLANGUAGE", APIUtils.getStringOrNull(tvSeries, "originalLanguage"));
					rs.updateString("ORIGINALTITLE", APIUtils.getStringOrNull(tvSeries, "originalTitle"));
					if (tvSeries.has("productionCompanies")) {
						rs.updateString("PRODUCTIONCOMPANIES", tvSeries.get("productionCompanies").toString());
					}
					if (tvSeries.has("productionCountries")) {
						rs.updateString("PRODUCTIONCOUNTRIES", tvSeries.get("productionCountries").toString());
					}
					if (tvSeries.has("seasons")) {
						rs.updateString("SEASONS", tvSeries.get("seasons").toString());
					}
					rs.updateString("SERIESTYPE", APIUtils.getStringOrNull(tvSeries, "seriesType"));
					if (tvSeries.has("spokenLanguages")) {
						rs.updateString("SPOKENLANGUAGES", tvSeries.get("spokenLanguages").toString());
					}
					rs.updateString("STATUS", APIUtils.getStringOrNull(tvSeries, "status"));
					rs.updateString("TAGLINE", APIUtils.getStringOrNull(tvSeries, "tagline"));
					rs.updateRow();
				} else {
					LOGGER.debug("Couldn't find \"{}\" in the database when trying to store data from our API", title);
				}
			}
		} catch (SQLException e) {
			LOGGER.error(LOG_ERROR_WHILE_VAR_IN, DATABASE_NAME, "inserting API data to TV series entry", simplifiedTitle, TABLE_NAME, e.getMessage());
		}
	}

	/**
	 * Removes an entry or entries by IMDb ID.
	 *
	 * @param connection the db connection
	 * @param imdbID the IMDb ID to remove
	 */
	public static void removeByImdbId(final Connection connection, final String imdbID) {
		try {
			try (PreparedStatement statement = connection.prepareStatement(SQL_DELETE_IMDBID)) {
				statement.setString(1, imdbID);
				int rows = statement.executeUpdate();
				LOGGER.trace("Removed entries {} in " + TABLE_NAME + " for imdbID \"{}\"", rows, imdbID);
			}
		} catch (SQLException e) {
			LOGGER.error(LOG_ERROR_WHILE_IN_FOR, DATABASE_NAME, "removing entries", TABLE_NAME, imdbID, e.getMessage());
			LOGGER.trace("", e);
		}
	}

	/**
	 * Removes an entry by ID.
	 *
	 * @param connection the db connection
	 * @param id the ID to remove
	 */
	public static void removeImdbIdById(final Connection connection, final Integer id) {
		try {
			try (PreparedStatement statement = connection.prepareStatement(SQL_UPDATE_IMDBID_NULL)) {
				statement.setInt(1, id);
				int row = statement.executeUpdate();
				LOGGER.trace("Removed IMDb ID from {} in " + TABLE_NAME + " for ID \"{}\"", row, id);
			}
		} catch (SQLException e) {
			LOGGER.error(LOG_ERROR_WHILE_IN_FOR, DATABASE_NAME, "removing entry", TABLE_NAME, id, e.getMessage());
			LOGGER.trace("", e);
		}
	}

	public static Boolean isFullyPlayed(final Connection connection, final String title) {
		try {
			/*
			 * If there is one file for this TV series where ISFULLYPLAYED is
			 * not true, then this series is not fully played, otherwise it is.
			 *
			 * This backwards logic is used for performance since we only have
			 * to check one row instead of all rows.
			 */
			try (PreparedStatement statement = connection.prepareStatement(SQL_GET_ISFULLYPLAYED)) {
				statement.setString(1, title);
				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("Searching " + TABLE_NAME + " with \"{}\"", statement);
				}
				try (ResultSet resultSet = statement.executeQuery()) {
					if (resultSet.next()) {
						return false;
					}
				}
			}
		} catch (SQLException e) {
			LOGGER.error(LOG_ERROR_WHILE_IN_FOR, DATABASE_NAME, "looking up TV series status", TABLE_NAME, title, e.getMessage());
			LOGGER.trace("", e);
		}

		return true;
	}

}
