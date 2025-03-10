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
import axios from 'axios';
import { showNotification } from '@mantine/notifications';
import { getJwt, redirectToLogin } from './auth-service';
import { authApiUrl } from '../utils';

axios.interceptors.request.use(function (request) {
  const jwt = getJwt();
  if (jwt && request !== undefined && request.headers !== undefined) {
    request.headers.Authorization = "Bearer " + jwt;
  }
  return request;
}, function (error) {
  // Do something with request error
  return Promise.reject(error);
});

axios.interceptors.response.use(function (response) {
  return response;
}, function (error) {
  if (error?.response?.status === 401 && error?.config?.url !== authApiUrl + 'login') {
    showNotification({
      id: 'authentication-error',
      color: 'red',
      title: 'Authentication error',
      message: 'You have been logged out from Universal Media Server. Please click here to log in again.',
      autoClose: false,
      onClick: redirectToLogin,
    });
  }
  return Promise.reject(error);
});
