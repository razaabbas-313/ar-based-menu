/**
 * API client (control plane only: small JSON documents, never file bytes) plus the direct
 * browser -> S3 uploader.
 *
 * JWT interceptor: there are two independent sessions. Requests marked `auth: 'restaurant'`
 * carry the restaurant (studio) token, `auth: 'admin'` the super-admin token. A 401 on such a
 * request clears only that session. Public guest routes never send a token.
 */

export const API_BASE = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '');

const R = 'restaurant';
const A = 'admin';
const tokens = { restaurant: null, admin: null };
const unauthorizedHandlers = { restaurant: null, admin: null };

export function setAuthToken(token) {
  tokens.restaurant = token || null;
}

export function setAdminToken(token) {
  tokens.admin = token || null;
}

export function setUnauthorizedHandler(handler, kind = 'restaurant') {
  unauthorizedHandlers[kind] = handler;
}

/** Problem title the API uses while the owner still has a temporary password. */
export const PASSWORD_CHANGE_REQUIRED = 'Password change required';
let passwordRequiredHandler = null;

/** Called when a studio request is refused because the password must be changed first. */
export function setPasswordRequiredHandler(handler) {
  passwordRequiredHandler = handler;
}

export class ApiError extends Error {
  constructor(message, { status = 0, fields = null, body = null } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fields = fields;
    this.body = body;
  }
}

function toQuery(params) {
  const q = new URLSearchParams();
  Object.entries(params || {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') q.set(k, String(v));
  });
  const s = q.toString();
  return s ? `?${s}` : '';
}

async function request(path, { method = 'GET', body, auth = null, timeout = 15000, fresh = false } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = auth ? tokens[auth] : null;
  if (token) headers.Authorization = `Bearer ${token}`;

  let res;
  try {
    res = await fetch(API_BASE + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal,
      cache: fresh || auth ? 'no-store' : 'default',
    });
  } catch (e) {
    if (e.name === 'AbortError') throw new ApiError('The Atelier server did not answer in time. Please try again.');
    throw new ApiError(`Can't reach the Atelier server${API_BASE ? ` at ${API_BASE}` : ''}. Is the API running?`);
  } finally {
    clearTimeout(timer);
  }

  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    if (res.status === 401 && auth && unauthorizedHandlers[auth]) unauthorizedHandlers[auth]();
    if (res.status === 403 && auth === R && data?.title === PASSWORD_CHANGE_REQUIRED && passwordRequiredHandler) passwordRequiredHandler();
    let message = (data && (data.detail || data.title || data.message || data.error));
    if (!message) {
      if (res.status === 413) {
        message = 'This photo is too large. Please choose an image under 10 MB.';
      } else if (res.status === 403) {
        message = 'Your session expired. Please sign in again.';
      } else if (res.status === 404) {
        message = 'The requested item was not found.';
      } else if (res.status >= 500) {
        message = 'Our cloud studio encountered a temporary pause. Please try again in a moment.';
      } else {
        message = `Unable to complete request (${res.status}).`;
      }
    }
    throw new ApiError(message, { status: res.status, fields: data?.fields || null, body: data });
  }
  return data;
}

export const api = {
  recordInsight: body=>request('/api/v1/public/insights/event',{method:'POST',body,fresh:true}),
  insights:(from,to)=>request('/api/v1/studio/insights'+toQuery({from,to}),{auth:R}),
  platformInsights:(from,to)=>request('/api/v1/admin/insights'+toQuery({from,to}),{auth:A}),
  draftImagePresign:(purpose,body)=>request('/api/v1/studio/appearance/assets/'+purpose+'/presign',{method:'POST',body,auth:R}),
  draftImageConfirm:id=>request('/api/v1/studio/appearance/assets/'+id+'/confirm',{method:'POST',auth:R}),
  studioModelRequests:()=>request('/api/v1/studio/model-requests',{auth:R}),
  appearance: () => request('/api/v1/studio/appearance',{auth:R}),
  saveAppearance: (expectedVersion,values) => request('/api/v1/studio/appearance/draft',{method:'PUT',body:{expectedVersion,values},auth:R}),
  publishAppearance: expectedVersion => request('/api/v1/studio/appearance/publish',{method:'POST',body:{expectedVersion},auth:R}),
  discardAppearance: expectedVersion => request('/api/v1/studio/appearance/discard',{method:'POST',body:{expectedVersion},auth:R}),
  reviewSettings: () => request('/api/v1/studio/reviews',{auth:R}),
  saveReviewSettings: body => request('/api/v1/studio/reviews',{method:'PUT',body,auth:R}),
  publicAppearance: slug => request('/api/v1/public/appearance/'+encodeURIComponent(slug),{fresh:true}),
  modelHistory: (dishId,after=0) => request('/api/v1/studio/dishes/'+dishId+'/model-history?after='+after,{auth:R}),
  adminModelHistory: (restaurantId,dishId,after=0) => request('/api/v1/admin/restaurants/'+restaurantId+'/dishes/'+dishId+'/model-history?after='+after,{auth:A}),
  adminDirectModelPresign: (restaurantId,dishId,body) => request('/api/v1/admin/restaurants/'+restaurantId+'/dishes/'+dishId+'/model/presign',{method:'POST',body,auth:A}),
  adminDirectModelConfirm: (restaurantId,dishId,body) => request('/api/v1/admin/restaurants/'+restaurantId+'/dishes/'+dishId+'/model/confirm',{method:'POST',body,auth:A}),
  experienceSettings: () => request('/api/v1/studio/experience', {auth:R}),
  saveExperienceSettings: body => request('/api/v1/studio/experience', {method:'PUT',body,auth:R}),
  tables: (after=0) => request('/api/v1/studio/tables?after='+encodeURIComponent(after), {auth:R}),
  createTable: name => request('/api/v1/studio/tables', {method:'POST',body:{name},auth:R}),
  updateTable: (id,body) => request('/api/v1/studio/tables/'+id, {method:'PUT',body,auth:R}),
  rotateTable: (id,expectedVersion) => request('/api/v1/studio/tables/'+id+'/rotate', {method:'POST',body:{expectedVersion},auth:R}),
  guestContext: (slug,tableToken) => request('/api/v1/public/context/'+encodeURIComponent(slug), {method:'POST',body:{tableToken},fresh:true}),
  prepareOrder: (slug,body) => request('/api/v1/public/order-preparations/'+encodeURIComponent(slug), {method:'POST',body,fresh:true}),
  waiterRequest: (slug,tableToken) => request('/api/v1/public/waiter-requests/'+encodeURIComponent(slug), {method:'POST',body:{tableToken},fresh:true}),
  entitlementRegistry: () => request('/api/v1/admin/features', { auth: A }),
  plans: () => request('/api/v1/admin/plans', { auth: A }),
  createPlan: (dto) => request('/api/v1/admin/plans', { method: 'POST', body: dto, auth: A }),
  revisePlan: (id,dto) => request('/api/v1/admin/plans/'+id, { method: 'PUT', body: dto, auth: A }),
  planRevisions: (id) => request('/api/v1/admin/plans/'+id+'/revisions', { auth: A }),
  restaurantEntitlements: (id) => request('/api/v1/admin/restaurants/'+id+'/entitlements', { auth: A }),
  assignPlan: (id,dto) => request('/api/v1/admin/restaurants/'+id+'/entitlements/assignment', { method: 'POST', body: dto, auth: A }),
  setEntitlementOverride: (id,key,dto) => request('/api/v1/admin/restaurants/'+id+'/entitlements/overrides/'+encodeURIComponent(key), { method: 'PUT', body: dto, auth: A }),
  removeEntitlementOverride: (id,key,expectedVersion,reason) => request('/api/v1/admin/restaurants/'+id+'/entitlements/overrides/'+encodeURIComponent(key), { method: 'DELETE', body: { expectedVersion, reason }, auth: A }),
  ownerEntitlements: () => request('/api/v1/studio/entitlements', { auth: R }),
  // ---- public (guests): a single menu by its direct link; there is no directory ----
  publicMenu: (slug, { fresh = false } = {}) =>
    request(`/api/v1/public/menu/${encodeURIComponent(slug)}`, { fresh }),
  trackEvent: (dto) => request('/api/v1/public/analytics/event', { method: 'POST', body: dto }),

  // ---- studio & admin preview ----
  studioMenuPreview: () => request('/api/v1/studio/menu-preview', { auth: R, fresh: true }),
  adminMenuPreview: (id) => request(`/api/v1/admin/restaurants/${id}/menu-preview`, { auth: A, fresh: true }),

  // ---- restaurant auth (accounts are provisioned by the super admin) ----
  authConfig: () => request('/api/v1/auth/config'),
  login: (dto) => request('/api/v1/auth/login', { method: 'POST', body: dto }),
  me: () => request('/api/v1/auth/me', { auth: R }),
  changePassword: (dto) => request('/api/v1/auth/change-password', { method: 'POST', body: dto, auth: R }),
  forgotPassword: (dto) => request('/api/v1/auth/forgot-password', { method: 'POST', body: dto }),
  resetPasswordOtp: (dto) => request('/api/v1/auth/reset-password-otp', { method: 'POST', body: dto }),

  // ---- studio: new dish (photo pipeline) ----
  presign: (dto) => request('/api/v1/media/presign', { method: 'POST', body: dto, auth: R }),
  confirm: (dto) => request('/api/v1/media/confirm', { method: 'POST', body: dto, auth: R }),
  // in-place photo / 3D model replacement
  replacePresign: (id, dto) => request(`/api/v1/media/${id}/replace-presign`, { method: 'POST', body: dto, auth: R }),
  replaceConfirm: (id, dto) => request(`/api/v1/media/${id}/replace-confirm`, { method: 'POST', body: dto, auth: R }),

  // ---- studio: categories ----
  categories: () => request('/api/v1/studio/categories', { auth: R }),
  createCategory: (dto) => request('/api/v1/studio/categories', { method: 'POST', body: dto, auth: R }),
  updateCategory: (id, dto) => request(`/api/v1/studio/categories/${id}`, { method: 'PUT', body: dto, auth: R }),
  deleteCategory: (id) => request(`/api/v1/studio/categories/${id}`, { method: 'DELETE', auth: R }),
  reorderCategories: (orderedCategoryIds) =>
    request('/api/v1/studio/categories/reorder', { method: 'PUT', body: { orderedCategoryIds }, auth: R }),

  // ---- studio: menu live status ----
  setMenuStatus: (isMenuLive) => request('/api/v1/studio/menu-status', { method: 'PUT', body: { isMenuLive }, auth: R }),

  // ---- studio: dishes ----
  studioDishes: () => request('/api/v1/studio/dishes', { auth: R }),
  updateDish: (id, dto) => request(`/api/v1/studio/dishes/${id}`, { method: 'PUT', body: dto, auth: R }),
  setAvailability: (id, available) =>
    request(`/api/v1/studio/dishes/${id}/availability`, { method: 'PATCH', body: { available }, auth: R }),
  deleteDish: (id) => request(`/api/v1/studio/dishes/${id}`, { method: 'DELETE', auth: R }),
  attach3d: (id, dto) => request(`/api/v1/studio/dishes/${id}/attach-3d`, { method: 'POST', body: dto, auth: R }),
  confirm3d: (id, dto) => request(`/api/v1/studio/dishes/${id}/confirm-3d`, { method: 'POST', body: dto, auth: R }),
  removeModel: (id) => request(`/api/v1/studio/dishes/${id}/model`, { method: 'DELETE', auth: R }),

  // ---- studio: 3D concierge request ----
  presignModelRequest: (dishId, items) =>
    request(`/api/v1/studio/dishes/${dishId}/model-request/presign`, { method: 'POST', body: items, auth: R }),
  confirmModelRequest: (dishId, dto) =>
    request(`/api/v1/studio/dishes/${dishId}/model-request/confirm`, { method: 'POST', body: dto, auth: R }),

  // ---- studio: analytics ----
  studioAnalytics: () => request('/api/v1/studio/analytics', { auth: R }),

  // ---- studio: branding ----
  settings: () => request('/api/v1/studio/settings', { auth: R }),
  updateSettings: (dto) => request('/api/v1/studio/settings', { method: 'PUT', body: dto, auth: R }),
  brandingPresign: (kind, dto) => request(`/api/v1/studio/branding/${kind}/presign`, { method: 'POST', body: dto, auth: R }),
  brandingConfirm: (kind, dto) => request(`/api/v1/studio/branding/${kind}/confirm`, { method: 'POST', body: dto, auth: R }),

  // ---- super admin ----
  adminLogin: (dto) => request('/api/v1/auth/admin/login', { method: 'POST', body: dto }),
  adminMe: () => request('/api/v1/admin/me', { auth: A }),
  adminChangePassword: (dto) => request('/api/v1/admin/me/password', { method: 'PUT', body: dto, auth: A }),
  analytics: () => request('/api/v1/admin/analytics', { auth: A }),
  adminRestaurants: (params) => request(`/api/v1/admin/restaurants${toQuery(params)}`, { auth: A }),
  adminRestaurant: (id) => request(`/api/v1/admin/restaurants/${id}`, { auth: A }),
  adminCreateRestaurant: (dto) => request('/api/v1/admin/restaurants', { method: 'POST', body: dto, auth: A }),
  adminUpdateRestaurant: (id, dto) => request(`/api/v1/admin/restaurants/${id}`, { method: 'PUT', body: dto, auth: A }),
  adminUpdateNotes: (id, notes) =>
    request(`/api/v1/admin/restaurants/${id}/notes`, { method: 'PUT', body: { notes }, auth: A }),
  adminSetStatus: (id, status, reason) =>
    request(`/api/v1/admin/restaurants/${id}/status`, { method: 'PUT', body: { status, reason }, auth: A }),
  adminDeleteRestaurant: (id, hard) =>
    request(`/api/v1/admin/restaurants/${id}${hard ? '?hard=true' : ''}`, { method: 'DELETE', auth: A }),
  adminRestore: (id) => request(`/api/v1/admin/restaurants/${id}/restore`, { method: 'POST', auth: A }),
  adminRecalculate: (id) => request(`/api/v1/admin/restaurants/${id}/recalculate-usage`, { method: 'POST', auth: A }),
  adminImpersonate: (id) => request(`/api/v1/admin/restaurants/${id}/impersonate`, { method: 'POST', auth: A }),
  adminResetPassword: (id) => request(`/api/v1/admin/restaurants/${id}/reset-password`, { method: 'POST', auth: A }),
  adminSlugAvailable: (slug, excludeId) => request(`/api/v1/admin/slug-available${toQuery({ slug, excludeId })}`, { auth: A }),

  // ---- super admin 3d pipeline desk ----
  admin3dRequests: () => request('/api/v1/admin/3d-requests', { auth: A }),
  admin3dRequestsHistory: (params) => {
    const query = typeof params === 'object' && params !== null ? params : (params !== undefined ? { restaurantId: params } : {});
    return request(`/api/v1/admin/3d-requests/history${toQuery(query)}`, { auth: A });
  },
  adminRestaurantModelRequests: (restaurantId) =>
    request(`/api/v1/admin/restaurants/${restaurantId}/model-requests`, { auth: A }),
  adminUpdate3dPermission: (id, canUpload3d) =>
    request(`/api/v1/admin/restaurants/${id}/permission-3d`, { method: 'PUT', body: { canUpload3d }, auth: A }),
  adminPresignModel: (dishId, dto) =>
    request(`/api/v1/admin/3d-requests/${dishId}/presign`, { method: 'POST', body: dto, auth: A }),
  adminApproveModel: (dishId, dto) =>
    request(`/api/v1/admin/3d-requests/${dishId}/approve`, { method: 'POST', body: dto, auth: A }),
};

/**
 * Streams a Blob/File straight from the browser to S3 with a presigned PUT URL.
 * XHR (not fetch) because it reports real upload progress.
 */
export function putToS3(url, blob, headers, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url, true);
    Object.entries(headers || {}).forEach(([k, v]) => xhr.setRequestHeader(k, v));
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) onProgress(e.loaded / e.total, e.loaded, e.total);
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve({ etag: (xhr.getResponseHeader('ETag') || '').replaceAll('"', '') });
      } else {
        const hint =
          xhr.status === 403
            ? ' Secure upload authorization expired. Please try again.'
            : '';
        reject(new ApiError(`Upload could not be saved (HTTP ${xhr.status}).${hint}`, { status: xhr.status }));
      }
    };
    xhr.onerror = () =>
      reject(
        new ApiError(
          'Network connection interrupted during photo upload. Please check your connection and try again.',
        ),
      );
    xhr.ontimeout = () => reject(new ApiError('Photo upload timed out. Please check your connection and try again.'));
    xhr.timeout = 10 * 60 * 1000;
    xhr.send(blob);
  });
}
