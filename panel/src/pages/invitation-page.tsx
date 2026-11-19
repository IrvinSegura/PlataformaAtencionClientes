import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { invitationsApi } from '../api/identity';
import { AuthLayout, TermsCheckbox } from '../components/auth-layout';
import { Icons } from '../components/icons';
import { ErrorAlert, Spinner } from '../components/ui';
import { ROLE_NAMES } from '../format';

const MIN_PASSWORD_LENGTH = 12;

// Where the token waits while the tab is open, so reloading the page after it left the address bar keeps it
const TOKEN_KEY = 'invitation-token';

/** The token in the link's fragment, which the page drops from the address bar once it is on screen. */
function tokenFromUrl(): string {
  const fragment = window.location.hash.slice(1);
  if (fragment) {
    try {
      return decodeURIComponent(fragment);
    } catch {
      // A link cut or mangled by a mail client: the server answers that the invitation is not valid
      return fragment;
    }
  }
  try {
    return sessionStorage.getItem(TOKEN_KEY) ?? '';
  } catch {
    return '';
  }
}

function rememberToken(token: string | null) {
  try {
    if (token) {
      sessionStorage.setItem(TOKEN_KEY, token);
    } else {
      sessionStorage.removeItem(TOKEN_KEY);
    }
  } catch {
    // Storage blocked: a reload asks for the link again
  }
}

/**
 * Opened from an invitation link, signed in or not. Joining with an existing account asks for its password; otherwise
 * the account is created here for the invited email.
 */
export function InvitationPage() {
  const [token] = useState(tokenFromUrl);
  // In an effect, not while rendering: React may render the page more than once before showing it (it loads on first
  // use), and a render that cleared the address bar would leave the next one without the token
  useEffect(() => {
    if (token) {
      rememberToken(token);
    }
    if (window.location.hash) {
      window.history.replaceState(window.history.state, '', window.location.pathname);
    }
  }, [token]);
  const navigate = useNavigate();
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [shortPassword, setShortPassword] = useState(false);
  const [acceptTerms, setAcceptTerms] = useState(false);
  const preview = useQuery({
    queryKey: ['invitation', token],
    queryFn: () => invitationsApi.preview(token),
    enabled: token !== '',
    retry: false,
    staleTime: Infinity,
  });
  const accept = useMutation({
    mutationFn: () => invitationsApi.accept(token, displayName.trim(), password, acceptTerms),
  });

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const newAccount = preview.data && !preview.data.accountExists;
    setShortPassword(Boolean(newAccount) && password.length < MIN_PASSWORD_LENGTH);
    if (newAccount && password.length < MIN_PASSWORD_LENGTH) {
      return;
    }
    try {
      // Awaited rather than onSuccess: a new session clears the query client, mutations included
      await accept.mutateAsync();
      rememberToken(null);
      navigate('/conversations', { replace: true });
    } catch {
      // Shown from accept.error
    }
  };

  return (
    <AuthLayout title="Invitación" footer={<Link to="/login">Ir a iniciar sesión</Link>}>
        {token === '' && (
          <p className="alert alert-danger" role="alert">
            El enlace está incompleto. Ábrelo tal como te lo enviaron.
          </p>
        )}
        {preview.isPending && token !== '' && <Spinner label="Revisando la invitación…" />}
        <ErrorAlert error={preview.error} />
        {preview.data && (
          <>
            <div className="invitation-summary">
              <span className="empty-icon">
                <Icons.Users size={20} />
              </span>
              <p>
                Te invitaron a <strong>{preview.data.workspaceName}</strong> como{' '}
                <strong>{ROLE_NAMES[preview.data.role].toLowerCase()}</strong>.
              </p>
            </div>
            <form className="form" onSubmit={onSubmit}>
              <label>
                Email
                <input value={preview.data.email} readOnly autoComplete="username" />
              </label>
              {!preview.data.accountExists && (
                <label>
                  Tu nombre
                  <input
                    value={displayName}
                    onChange={(event) => setDisplayName(event.target.value)}
                    required
                    maxLength={80}
                    autoComplete="name"
                  />
                </label>
              )}
              <label>
                {preview.data.accountExists ? 'Tu contraseña actual' : 'Crea una contraseña'}
                <input
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  required
                  maxLength={256}
                  autoComplete={preview.data.accountExists ? 'current-password' : 'new-password'}
                />
              </label>
              {!preview.data.accountExists && <p className="muted small">Al menos {MIN_PASSWORD_LENGTH} caracteres.</p>}
              {!preview.data.accountExists && (
                <TermsCheckbox checked={acceptTerms} onChange={setAcceptTerms} />
              )}
              {shortPassword && (
                <p className="alert alert-danger" role="alert">
                  Usa al menos {MIN_PASSWORD_LENGTH} caracteres.
                </p>
              )}
              <ErrorAlert error={accept.error} />
              <button type="submit" className="button button-primary" disabled={accept.isPending}>
                {accept.isPending ? 'Uniéndote…' : 'Unirme al equipo'}
              </button>
            </form>
          </>
        )}
    </AuthLayout>
  );
}
