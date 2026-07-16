import { HttpInterceptorFn } from '@angular/common/http';

const ENTRA_API_ACCESS_TOKEN_STORAGE_KEY = 'entraApiAccessToken';
const ENTRA_GRAPH_ACCESS_TOKEN_STORAGE_KEY = 'entraGraphAccessToken';
const GRAPH_ACCESS_TOKEN_HEADER = 'X-Graph-Access-Token';

export const authTokenInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.headers.has('Authorization') || isPublicRequest(request.url)) {
    return next(request);
  }

  const token = sessionStorage.getItem(ENTRA_API_ACCESS_TOKEN_STORAGE_KEY);
  if (!token || token.trim().length === 0) {
    return next(request);
  }

  const graphAccessToken = sessionStorage.getItem(ENTRA_GRAPH_ACCESS_TOKEN_STORAGE_KEY);
  const setHeaders: Record<string, string> = {
    Authorization: `Bearer ${token}`
  };

  if (graphAccessToken && graphAccessToken.trim().length > 0) {
    setHeaders[GRAPH_ACCESS_TOKEN_HEADER] = graphAccessToken;
  }

  return next(request.clone({
    setHeaders
  }));
};

function isPublicRequest(url: string): boolean {
  const normalizedUrl = url.toLowerCase();
  return normalizedUrl.includes('/auth/login') || normalizedUrl.includes('/auth/entra/config');
}