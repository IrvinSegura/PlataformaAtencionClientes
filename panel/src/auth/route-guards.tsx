import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from './auth-context';
import { Spinner } from '../components/ui';

export function RequireAuth() {
  const { state } = useAuth();
  const location = useLocation();
  if (state.status === 'loading') {
    return (
      <div className="boot">
        <Spinner />
      </div>
    );
  }
  if (state.status === 'signedOut') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <Outlet />;
}

/** Login and registration: a signed-in operator goes straight to the panel. */
export function GuestOnly() {
  const { state } = useAuth();
  const location = useLocation();
  if (state.status === 'loading') {
    return (
      <div className="boot">
        <Spinner />
      </div>
    );
  }
  if (state.status === 'signedIn') {
    const from = (location.state as { from?: string } | null)?.from;
    // Only paths inside the panel; never a URL that could send the operator to another site
    const target = from && from.startsWith('/') && !from.startsWith('//') ? from : '/conversations';
    return <Navigate to={target} replace />;
  }
  return <Outlet />;
}
