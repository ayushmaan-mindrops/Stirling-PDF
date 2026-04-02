import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { Text, Stack, Alert, PasswordInput, Button, Progress, TextInput } from '@mantine/core';
import { springAuth } from '@app/auth/springAuthClient';
import { useAuth } from '@app/auth/UseSession';
import { useAppConfig } from '@app/contexts/AppConfigContext';
import { useTranslation } from 'react-i18next';
import { useDocumentMeta } from '@app/hooks/useDocumentMeta';
import AuthLayout from '@app/routes/authShared/AuthLayout';
import { useBackendProbe } from '@app/hooks/useBackendProbe';
import apiClient from '@app/services/apiClient';
import { BASE_PATH } from '@app/constants/app';
import { type OAuthProvider } from '@app/auth/oauthTypes';
import { updateSupportedLanguages } from '@app/i18n';

// Import login components
import LoginHeader from '@app/routes/login/LoginHeader';
import ErrorMessage from '@app/routes/login/ErrorMessage';
import EmailPasswordForm from '@app/routes/login/EmailPasswordForm';
import OAuthButtons, { DEBUG_SHOW_ALL_PROVIDERS, oauthProviderConfig } from '@app/routes/login/OAuthButtons';
import DividerWithText from '@app/components/shared/DividerWithText';
import LoggedInState from '@app/routes/login/LoggedInState';

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const { session, loading } = useAuth();
  const { refetch } = useAppConfig();
  const { t } = useTranslation();
  const [isSigningIn, setIsSigningIn] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [showEmailForm, setShowEmailForm] = useState(false);
  const [email, setEmail] = useState(() => searchParams.get('email') ?? '');
  const [password, setPassword] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [requiresMfa, setRequiresMfa] = useState(false);
  const [enabledProviders, setEnabledProviders] = useState<OAuthProvider[]>([]);
  const [hasSSOProviders, setHasSSOProviders] = useState(false);
  const [_enableLogin, setEnableLogin] = useState<boolean | null>(null);
  const [loginMethod, setLoginMethod] = useState<string>('all');
  const [ssoAutoLogin, setSsoAutoLogin] = useState(false);
  const backendProbe = useBackendProbe();
  const [isFirstTimeSetup, setIsFirstTimeSetup] = useState(false);
  const [showDefaultCredentials, setShowDefaultCredentials] = useState(false);
  // Marketplace setup state
  const [marketplaceNeedsSetup, setMarketplaceNeedsSetup] = useState(false);
  const [isCheckingSetup, setIsCheckingSetup] = useState(false);
  const [isSettingUpAccount, setIsSettingUpAccount] = useState(false);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [marketplacePlanId, setMarketplacePlanId] = useState('');
  const loginDisabled = backendProbe.loginDisabled === true || _enableLogin === false;
  const autoLoginAttempted = useRef(false);
  const autoLoginErrorRecorded = useRef(false);
  const isUserPassAllowed = loginMethod === 'all' || loginMethod === 'normal';
  const isSsoOnlyMode = loginMethod !== 'all' && loginMethod !== 'normal';
  const isSingleSsoOnly = !isUserPassAllowed && enabledProviders.length === 1;

  const AUTO_LOGIN_ATTEMPTS_KEY = 'stirling_sso_auto_login_attempts';
  const AUTO_LOGIN_ERRORS_KEY = 'stirling_sso_auto_login_errors';
  const AUTO_LOGIN_LOGOUT_KEY = 'stirling_sso_auto_login_logged_out';
  const MAX_AUTO_LOGIN_ATTEMPTS = 2;
  const MAX_AUTO_LOGIN_ERRORS = 1;

  const readSessionNumber = (key: string) => {
    if (typeof window === 'undefined') {
      return 0;
    }
    const raw = window.sessionStorage.getItem(key);
    const value = Number(raw);
    return Number.isFinite(value) ? value : 0;
  };

  const writeSessionNumber = (key: string, value: number) => {
    if (typeof window === 'undefined') {
      return;
    }
    window.sessionStorage.setItem(key, String(value));
  };

  const hasLogoutBlock = () => {
    if (typeof window === 'undefined') {
      return false;
    }
    return window.sessionStorage.getItem(AUTO_LOGIN_LOGOUT_KEY) === '1';
  };

  const clearLogoutBlock = () => {
    if (typeof window === 'undefined') {
      return;
    }
    window.sessionStorage.removeItem(AUTO_LOGIN_LOGOUT_KEY);
  };

  const recordAutoLoginAttempt = () => {
    const attempts = readSessionNumber(AUTO_LOGIN_ATTEMPTS_KEY);
    writeSessionNumber(AUTO_LOGIN_ATTEMPTS_KEY, attempts + 1);
  };

  const recordAutoLoginError = () => {
    const errors = readSessionNumber(AUTO_LOGIN_ERRORS_KEY);
    writeSessionNumber(AUTO_LOGIN_ERRORS_KEY, errors + 1);
  };

  const errorFromState = (location.state as { error?: string } | null)?.error;
  const errorFromQuery = useMemo(() => {
    if (!searchParams) {
      return null;
    }
    const errorParamKeys = ['error', 'error_description', 'error_code', 'sso_error', 'oauth_error', 'saml_error', 'login_error'];
    for (const key of errorParamKeys) {
      const value = searchParams.get(key);
      if (value) {
        return value;
      }
    }
    for (const [key, value] of searchParams.entries()) {
      if (key.toLowerCase().includes('error')) {
        return value || 'Single sign-on failed. Please try again.';
      }
    }
    return null;
  }, [searchParams]);

  const hasSsoLoginError = Boolean(errorFromState || errorFromQuery);

  // Periodically probe while backend isn't up so the screen can auto-advance when it comes online
  useEffect(() => {
    if (backendProbe.status === 'up' || backendProbe.loginDisabled) {
      return;
    }
    const tick = async () => {
      const result = await backendProbe.probe();
      if (result.status === 'up') {
        await refetch();
        if (loginDisabled) {
          navigate('/', { replace: true });
        }
      }
    };
    const intervalId = window.setInterval(() => {
      void tick();
    }, 5000);
    return () => window.clearInterval(intervalId);
  }, [backendProbe.status, backendProbe.loginDisabled, backendProbe.probe, refetch, navigate, loginDisabled]);

  // Redirect immediately if user has valid session (JWT already validated by AuthProvider)
  useEffect(() => {
    if (!loading && session) {
      const returnPath = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname;
      console.debug('[Login] User already authenticated, redirecting to home', { returnPath });
      navigate(returnPath || '/', { replace: true });
    }
  }, [session, loading, navigate, location.state]);

  // If backend reports login is disabled, redirect to home (anonymous mode)
  useEffect(() => {
    if (backendProbe.loginDisabled) {
      // Slight delay to allow state updates before redirecting
      const id = setTimeout(() => navigate('/', { replace: true }), 0);
      return () => clearTimeout(id);
    }
  }, [backendProbe.loginDisabled, navigate]);

  useEffect(() => {
    if (backendProbe.status === 'up') {
      void refetch();
    }
  }, [backendProbe.status, refetch]);

  // Fetch enabled SSO providers and login config from backend
  useEffect(() => {
    const fetchProviders = async () => {
      try {
        const response = await apiClient.get('/api/v1/proprietary/ui-data/login');
        const data = response.data;

        // Check if login is disabled - if so, redirect to home
        if (data.enableLogin === false) {
          console.debug('[Login] Login disabled, redirecting to home');
          navigate('/');
          return;
        }

        setEnableLogin(data.enableLogin ?? true);
        setSsoAutoLogin(Boolean(data.ssoAutoLogin));

        // Set first-time setup flags
        setIsFirstTimeSetup(data.firstTimeSetup ?? false);
        setShowDefaultCredentials(data.showDefaultCredentials ?? false);

        // Apply language configuration from server
        if (data.languages || data.defaultLocale) {
          updateSupportedLanguages(data.languages, data.defaultLocale);
        }

        // Use the full paths from providerList as provider identifiers
        // The backend provides paths like "/oauth2/authorization/google" or "/saml2/authenticate/stirling"
        // We'll use these full paths so the auth client knows where to redirect
        const providerPaths = Object.keys(data.providerList || {});

        setEnabledProviders(providerPaths);
        setLoginMethod(data.loginMethod || 'all');
      } catch (err) {
        console.error('[Login] Failed to fetch enabled providers:', err);
        // Set default values on error to ensure UI remains functional
        // Login method defaults to 'all' to show both SSO and email/password options
        setEnableLogin(true);
        setLoginMethod('all');
        setEnabledProviders([]);
      }
    };

    if (backendProbe.status === 'up' || backendProbe.loginDisabled) {
      fetchProviders();
    }
  }, [navigate, backendProbe.status, backendProbe.loginDisabled]);

  // Update hasSSOProviders and showEmailForm when enabledProviders or loginMethod changes
  useEffect(() => {
    // In debug mode, check if any providers exist in the config
    const hasProviders = DEBUG_SHOW_ALL_PROVIDERS
      ? Object.keys(oauthProviderConfig).length > 0
      : enabledProviders.length > 0;
    setHasSSOProviders(hasProviders);

    // Check if username/password authentication is allowed
    const isUserPassAllowed = loginMethod === 'all' || loginMethod === 'normal';

    // Show email form if no SSO providers exist AND username/password is allowed
    if (!hasProviders && isUserPassAllowed) {
      setShowEmailForm(true);
    } else if (!isUserPassAllowed) {
      // Hide email form if username/password auth is not allowed
      setShowEmailForm(false);
    }
  }, [enabledProviders, loginMethod]);

  const signInWithProvider = async (provider: OAuthProvider) => {
    try {
      setIsSigningIn(true);
      setError(null);
      clearLogoutBlock();

      console.log(`[Login] Signing in with provider: ${provider}`);

      // Redirect to Spring OAuth2 endpoint using the actual provider ID from backend
      // The backend returns the correct registration ID (e.g., 'authentik', 'oidc', 'keycloak')
      const { error } = await springAuth.signInWithOAuth({
        provider: provider,
        options: { redirectTo: `${BASE_PATH}/auth/callback` }
      });

      if (error) {
        console.error(`[Login] ${provider} error:`, error);
        setError(t('login.failedToSignIn', { provider, message: error.message }) || `Failed to sign in with ${provider}`);
      }
    } catch (err) {
      console.error(`[Login] Unexpected error:`, err);
      setError(t('login.unexpectedError', { message: err instanceof Error ? err.message : 'Unknown error' }) || 'An unexpected error occurred');
    } finally {
      setIsSigningIn(false);
    }
  };

  // Auto-login to SSO when enabled and only one SSO option exists
  useEffect(() => {
    if (autoLoginAttempted.current) {
      return;
    }

    const attempts = readSessionNumber(AUTO_LOGIN_ATTEMPTS_KEY);
    const errors = readSessionNumber(AUTO_LOGIN_ERRORS_KEY);
    const blockedByErrors = errors >= MAX_AUTO_LOGIN_ERRORS;
    const blockedByAttempts = attempts >= MAX_AUTO_LOGIN_ATTEMPTS;
    const blockedByLogout = hasLogoutBlock();

    if (!ssoAutoLogin || loginDisabled || loading || session || backendProbe.status !== 'up') {
      return;
    }

    if (hasSsoLoginError || blockedByErrors || blockedByAttempts || blockedByLogout) {
      return;
    }

    if (isUserPassAllowed) {
      return;
    }

    if (enabledProviders.length !== 1) {
      return;
    }

    autoLoginAttempted.current = true;
    recordAutoLoginAttempt();
    void signInWithProvider(enabledProviders[0]);
  }, [
    ssoAutoLogin,
    loginDisabled,
    loading,
    session,
    backendProbe.status,
    loginMethod,
    enabledProviders,
    signInWithProvider,
    hasSsoLoginError,
  ]);

  // Handle query params (email prefill, success messages, and session expiry)
  useEffect(() => {
    try {
      const emailFromQuery = searchParams.get('email');
      if (emailFromQuery) {
        setEmail(emailFromQuery);
      }

      // Check if session expired (401 redirect)
      const expired = searchParams.get('expired');
      if (expired === 'true') {
        setError(t('login.sessionExpired', 'Your session has expired. Please sign in again.'));
      }

      const messageType = searchParams.get('messageType')
      if (messageType) {
        switch (messageType) {
          case 'accountCreated':
            setSuccessMessage(t('login.accountCreatedSuccess', 'Account created successfully! You can now sign in.'))
            break
          case 'passwordChanged':
            setSuccessMessage(t('login.passwordChangedSuccess', 'Password changed successfully! Please sign in with your new password.'))
            break
          case 'credsUpdated':
            setSuccessMessage(t('login.credentialsUpdated', 'Your credentials have been updated. Please sign in again.'))
            break
        }
      }

      if (errorFromState) {
        setError(errorFromState);
      } else if (errorFromQuery) {
        setError(errorFromQuery);
      }

      if (hasSsoLoginError && !autoLoginErrorRecorded.current) {
        recordAutoLoginError();
        autoLoginErrorRecorded.current = true;
      }
    } catch (_) {
      // ignore
    }
  }, [searchParams, t, errorFromState, errorFromQuery, hasSsoLoginError]);

  // Check if marketplace user needs to set up their account (create password)
  useEffect(() => {
    const checkMarketplaceSetup = async () => {
      const subscriptionId = searchParams.get('subscription');
      const emailParam = searchParams.get('email');
      const isMarketplace = searchParams.get('marketplace') === '1';

      if (!isMarketplace || !subscriptionId) {
        return;
      }

      setIsCheckingSetup(true);
      try {
        const params: Record<string, string> = { subscriptionId };
        if (emailParam) {
          params.email = emailParam;
        }

        const response = await apiClient.get('/api/v1/marketplace/setup-status', { params });

        const data = response.data;
        if (data.needsSetup) {
          setMarketplaceNeedsSetup(true);
          setMarketplacePlanId(data.planId || 'basic');
          // Use email from API response (backend resolves from subscription if URL didn't include it)
          setEmail(data.email || emailParam || '');
        }
      } catch (err) {
        console.error('[Login] Failed to check marketplace setup status:', err);
        // If we can't confirm setup status, don't leave users stuck on normal login.
        // Default to showing the marketplace password creation form.
        setMarketplaceNeedsSetup(true);
        setMarketplacePlanId('basic');
      } finally {
        setIsCheckingSetup(false);
      }
    };

    if (backendProbe.status === 'up' || searchParams.get('marketplace') === '1') {
      checkMarketplaceSetup();
    }
  }, [searchParams, backendProbe.status]);

  // Handle marketplace account setup (password creation)
  const handleMarketplaceSetup = useCallback(async () => {
    const subscriptionId = searchParams.get('subscription');
    
    if (!subscriptionId) {
      setError(t('marketplace.missingParams', 'Missing subscription information.'));
      return;
    }

    if (!email || !email.trim()) {
      setError(t('marketplace.missingEmail', 'Please enter your email.'));
      return;
    }

    if (newPassword.length < 8) {
      setError(t('marketplace.passwordTooShort', 'Password must be at least 8 characters'));
      return;
    }

    if (newPassword !== confirmPassword) {
      setError(t('marketplace.passwordMismatch', 'Passwords do not match'));
      return;
    }

    setIsSettingUpAccount(true);
    setError(null);

    try {
      const response = await apiClient.post('/api/v1/marketplace/setup-account', {
        subscriptionId,
        // Email can be omitted if backend resolves it from the subscription record.
        email: email.trim(),
        password: newPassword
      });

      if (response.data.status === 'success') {
        // Reset setup state and show success message
        setMarketplaceNeedsSetup(false);
        setNewPassword('');
        setConfirmPassword('');
        setSuccessMessage(t('login.accountCreatedSuccess', 'Account created successfully! You can now sign in.'));
        // Pre-fill password for convenience
        setPassword(newPassword);
      } else {
        setError(response.data.message || t('marketplace.setupFailed', 'Failed to create account'));
      }
    } catch (err: unknown) {
      console.error('[Login] Failed to setup marketplace account:', err);
      const errorMessage = err instanceof Error ? err.message : 'Failed to create account';
      setError(errorMessage);
    } finally {
      setIsSettingUpAccount(false);
    }
  }, [searchParams, email, newPassword, confirmPassword, t]);

  // Password strength helper
  const getPasswordStrength = (pwd: string): number => {
    let strength = 0;
    if (pwd.length >= 8) strength += 25;
    if (pwd.length >= 12) strength += 15;
    if (/[a-z]/.test(pwd)) strength += 15;
    if (/[A-Z]/.test(pwd)) strength += 15;
    if (/[0-9]/.test(pwd)) strength += 15;
    if (/[^a-zA-Z0-9]/.test(pwd)) strength += 15;
    return Math.min(100, strength);
  };

  const getStrengthColor = (strength: number): string => {
    if (strength < 30) return 'red';
    if (strength < 60) return 'orange';
    if (strength < 80) return 'yellow';
    return 'green';
  };

  const baseUrl = window.location.origin + BASE_PATH;

  // Set document meta
  useDocumentMeta({
    title: `${t('login.title', 'Sign in')} - Stirling PDF`,
    description: t('app.description', 'The Free Adobe Acrobat alternative (10M+ Downloads)'),
    ogTitle: `${t('login.title', 'Sign in')} - Stirling PDF`,
    ogDescription: t('app.description', 'The Free Adobe Acrobat alternative (10M+ Downloads)'),
    ogImage: `${baseUrl}/og_images/home.png`,
    ogUrl: `${window.location.origin}${window.location.pathname}`
  });

  // If login is disabled, short-circuit to home (avoids rendering the form after retry)
  if (loginDisabled) {
    return <Navigate to="/" replace />;
  }

  // Show logged in state if authenticated
  if (session && !loading) {
    return <LoggedInState />;
  }

  // Check if this is a Marketplace redirect - if so, skip the backend probe check
  // since we know the backend is up (the welcome page just loaded from it)
  const isMarketplaceRedirect = searchParams.get('marketplace') === '1';

  // If backend isn't ready yet, show a lightweight status screen instead of the form
  // Skip this check for Marketplace redirects since we know backend is up
  if (backendProbe.status !== 'up' && !loginDisabled && !isMarketplaceRedirect) {
    const backendTitle = t('backendStartup.notFoundTitle', 'Backend not found');
    const handleRetry = async () => {
      const result = await backendProbe.probe();
      if (result.status === 'up') {
        await refetch();
        // Stay on /login with query string (e.g. marketplace=1&subscription=...) — do not send to /
        navigate(
          { pathname: location.pathname, search: location.search },
          { replace: true }
        );
      }
    };
    return (
      <AuthLayout>
        <LoginHeader title={backendTitle} />
        <div
          className="auth-section"
          style={{
            padding: '1.5rem',
            marginTop: '1rem',
            borderRadius: '0.75rem',
            backgroundColor: 'rgba(37, 99, 235, 0.08)',
            border: '1px solid rgba(37, 99, 235, 0.2)',
          }}
        >
          <p style={{ margin: '0 0 0.75rem 0', color: 'rgba(15, 23, 42, 0.8)' }}>
            {t('backendStartup.unreachable')}
          </p>
          <button
            type="button"
            onClick={handleRetry}
            className="auth-cta-button px-4 py-[0.75rem] rounded-[0.625rem] text-base font-semibold mt-5 border-0 cursor-pointer"
            style={{ width: 'fit-content' }}
          >
            {t('backendStartup.retry', 'Retry')}
          </button>
        </div>
      </AuthLayout>
    );
  }

  const signInWithEmail = async () => {
    if (!email || !password) {
      setError(t('login.pleaseEnterBoth') || 'Please enter both email and password');
      return;
    }

    if (requiresMfa && !mfaCode.trim()) {
      setError(t('login.mfaRequired', 'Two-factor code required'));
      return;
    }

    try {
      setIsSigningIn(true);
      setError(null);
      clearLogoutBlock();

      // Store subscription ID for post-auth linking if this is a Marketplace redirect
      const subscriptionId = searchParams.get('subscription');
      if (subscriptionId) {
        sessionStorage.setItem('paperbolt_marketplace_subscription', subscriptionId);
      }

      console.log('[Login] Signing in with email:', email);

      const { user, session, error } = await springAuth.signInWithPassword({
        email: email.trim(),
        password: password,
        mfaCode: requiresMfa ? mfaCode.trim() : undefined,
      });

      if (error) {
        console.error('[Login] Email sign in error:', error);
        setError(error.message);
        if (error.mfaRequired || error.code === 'invalid_mfa_code') {
          setRequiresMfa(true);
        }
      } else if (user && session) {
        console.log('[Login] Email sign in successful');
        clearLogoutBlock();
        setRequiresMfa(false);
        setMfaCode('');
        // Auth state will update automatically and Landing will redirect to home
        // No need to navigate manually here
      }
    } catch (err) {
      console.error('[Login] Unexpected error:', err);
      setError(t('login.unexpectedError', { message: err instanceof Error ? err.message : 'Unknown error' }) || 'An unexpected error occurred');
    } finally {
      setIsSigningIn(false);
    }
  };

  // Forgot password handler (currently unused, reserved for future implementation)
  // const handleForgotPassword = () => {
  //   navigate('/auth/reset');
  // };

  // Show loading state while checking marketplace setup
  if (isCheckingSetup) {
    return (
      <AuthLayout>
        <LoginHeader title={t('marketplace.verifying', 'Verifying subscription...')} />
        <div style={{ textAlign: 'center', padding: '2rem' }}>
          <Text size="sm" c="dimmed">
            {t('marketplace.pleaseWait', 'Please wait while we verify your subscription...')}
          </Text>
        </div>
      </AuthLayout>
    );
  }

  // Show password creation form for marketplace users who need to set up their account
  if (marketplaceNeedsSetup) {
    const passwordStrength = getPasswordStrength(newPassword);
    const strengthColor = getStrengthColor(passwordStrength);

    return (
      <AuthLayout>
        <LoginHeader 
          title={t('marketplace.createPassword', 'Create Your Password')} 
          subtitle={t('marketplace.setupSubtitle', 'Set up your account to access your Azure Marketplace subscription')}
        />

        {/* Subscription info banner */}
        <div style={{
          padding: '1rem',
          marginBottom: '1.5rem',
          backgroundColor: 'rgba(102, 126, 234, 0.1)',
          border: '1px solid rgba(102, 126, 234, 0.3)',
          borderRadius: '0.5rem',
        }}>
          <p style={{ margin: 0, fontSize: '0.875rem', color: '#4c51bf', fontWeight: 600 }}>
            🎉 {t('marketplace.subscriptionActivated', 'Azure Marketplace Subscription Activated!')}
          </p>
          <p style={{ margin: '0.5rem 0 0 0', fontSize: '0.8rem', color: '#667eea' }}>
            {t('marketplace.planInfo', 'Plan: {{plan}}', { plan: marketplacePlanId.charAt(0).toUpperCase() + marketplacePlanId.slice(1) })}
          </p>
        </div>

        <ErrorMessage error={error} />

        <form onSubmit={(e) => { e.preventDefault(); handleMarketplaceSetup(); }}>
          <div className="auth-fields">
            {/* Email (editable fallback) */}
            <div className="auth-field">
              <label className="auth-label">{t('login.email', 'Email')}</label>
              <TextInput
                value={email}
                onChange={(e) => setEmail(e.currentTarget.value)}
                placeholder={t('marketplace.enterEmail', 'Enter your email')}
                inputMode="email"
                autoComplete="email"
                styles={{
                  input: {
                    backgroundColor: 'var(--auth-input-bg-light-only, #f8fafc)',
                    color: 'var(--auth-input-text-light-only, #1e293b)',
                    borderColor: 'var(--auth-input-border-light-only, #e2e8f0)'
                  },
                }}
              />
            </div>

            {/* Password field */}
            <div className="auth-field">
              <PasswordInput
                id="newPassword"
                label={t('marketplace.newPassword', 'New Password')}
                name="new-password"
                autoComplete="new-password"
                placeholder={t('marketplace.enterPassword', 'Enter your password')}
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                styles={{
                  input: {
                    backgroundColor: 'var(--auth-input-bg-light-only, #f8fafc)',
                    color: 'var(--auth-input-text-light-only, #1e293b)',
                    borderColor: 'var(--auth-input-border-light-only, #e2e8f0)',
                  },
                  label: {
                    color: 'var(--auth-label-text-light-only, #475569)',
                  },
                }}
                autoFocus
              />
              {newPassword && (
                <div style={{ marginTop: '0.5rem' }}>
                  <Progress 
                    value={passwordStrength} 
                    color={strengthColor}
                    size="xs"
                    radius="xl"
                  />
                  <Text size="xs" c="dimmed" mt={4}>
                    {passwordStrength < 30 && t('marketplace.passwordWeak', 'Weak password')}
                    {passwordStrength >= 30 && passwordStrength < 60 && t('marketplace.passwordFair', 'Fair password')}
                    {passwordStrength >= 60 && passwordStrength < 80 && t('marketplace.passwordGood', 'Good password')}
                    {passwordStrength >= 80 && t('marketplace.passwordStrong', 'Strong password')}
                  </Text>
                </div>
              )}
            </div>

            {/* Confirm password field */}
            <div className="auth-field">
              <PasswordInput
                id="confirmPassword"
                label={t('marketplace.confirmPassword', 'Confirm Password')}
                name="confirm-password"
                autoComplete="new-password"
                placeholder={t('marketplace.confirmPasswordPlaceholder', 'Confirm your password')}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                error={confirmPassword && newPassword !== confirmPassword ? t('marketplace.passwordMismatch', 'Passwords do not match') : undefined}
                styles={{
                  input: {
                    backgroundColor: 'var(--auth-input-bg-light-only, #f8fafc)',
                    color: 'var(--auth-input-text-light-only, #1e293b)',
                    borderColor: 'var(--auth-input-border-light-only, #e2e8f0)',
                  },
                  label: {
                    color: 'var(--auth-label-text-light-only, #475569)',
                  },
                }}
              />
            </div>
          </div>

          <Button
            type="submit"
            disabled={isSettingUpAccount || !email?.trim() || !newPassword || !confirmPassword || newPassword !== confirmPassword || newPassword.length < 8}
            className="auth-button auth-cta-button"
            fullWidth
            loading={isSettingUpAccount}
            style={{ marginTop: '1.5rem' }}
          >
            {isSettingUpAccount 
              ? t('marketplace.creatingAccount', 'Creating account...') 
              : t('marketplace.createAccount', 'Create Account & Continue')}
          </Button>
        </form>

        {/* Already have an account link */}
        <div style={{ marginTop: '1.5rem', textAlign: 'center' }}>
          <button
            type="button"
            onClick={() => setMarketplaceNeedsSetup(false)}
            style={{
              background: 'none',
              border: 'none',
              color: '#667eea',
              cursor: 'pointer',
              fontSize: '0.875rem',
              textDecoration: 'underline'
            }}
          >
            {t('marketplace.alreadyHaveAccount', 'Already have an account? Sign in')}
          </button>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout>
      <LoginHeader
        title={isSingleSsoOnly ? '' : (t('login.login') || 'Sign in')}
        centerOnly={isSingleSsoOnly}
      />

      {/* Marketplace welcome banner */}
      {isMarketplaceRedirect && !marketplaceNeedsSetup && (
        <div style={{
          padding: '1rem',
          marginBottom: '1rem',
          backgroundColor: 'rgba(102, 126, 234, 0.1)',
          border: '1px solid rgba(102, 126, 234, 0.3)',
          borderRadius: '0.5rem',
        }}>
          <p style={{ margin: 0, fontSize: '0.875rem', textAlign: 'center', color: '#4c51bf', fontWeight: 600 }}>
            🎉 Azure Marketplace Purchase Complete!
          </p>
          <p style={{ margin: '0.5rem 0 0 0', fontSize: '0.8rem', textAlign: 'center', color: '#667eea' }}>
            Sign in to activate your subscription.
          </p>
        </div>
      )}

      {/* Success message */}
      {successMessage && (
        <div style={{
          padding: '1rem',
          marginBottom: '1rem',
          backgroundColor: 'rgba(34, 197, 94, 0.1)',
          border: '1px solid rgba(34, 197, 94, 0.3)',
          borderRadius: '0.5rem',
          color: '#16a34a'
        }}>
          <p style={{ margin: 0, fontSize: '0.875rem', textAlign: 'center' }}>
            {successMessage}
          </p>
        </div>
      )}

      <ErrorMessage error={error} />

      {/* OAuth first */}
      <OAuthButtons
        onProviderClick={signInWithProvider}
        isSubmitting={isSigningIn}
        layout="vertical"
        enabledProviders={enabledProviders}
        ctaPrefix={isSsoOnlyMode ? t('login.signInWith', 'Sign in with') : undefined}
        styleVariant="light"
        useNewStyle={isSsoOnlyMode}
      />

      {/* Divider between OAuth and Email - only show if SSO is available and username/password is allowed */}
      {hasSSOProviders && isUserPassAllowed && (
        <DividerWithText text={t('signup.or', 'or')} respondsToDarkMode={false} opacity={0.4} />
      )}

      {/* Sign in with email button - only show if SSO providers exist and username/password is allowed */}
      {hasSSOProviders && !showEmailForm && isUserPassAllowed && (
        <div className="auth-section">
          <button
            type="button"
            onClick={() => setShowEmailForm(true)}
            disabled={isSigningIn}
            className="w-full px-4 py-[0.75rem] rounded-[0.625rem] text-base font-semibold mb-2 cursor-pointer border-0 disabled:opacity-50 disabled:cursor-not-allowed auth-cta-button"
          >
            {t('login.useEmailInstead', 'Login with email')}
          </button>
        </div>
      )}

      {/* Email form - show by default if no SSO, or when button clicked, but ONLY if username/password is allowed */}
      {showEmailForm && isUserPassAllowed && (
        <div style={{ marginTop: hasSSOProviders ? '1rem' : '0' }}>
          <EmailPasswordForm
            email={email}
            password={password}
            setEmail={setEmail}
            setPassword={setPassword}
            mfaCode={mfaCode}
            setMfaCode={setMfaCode}
            showMfaField={requiresMfa || Boolean(mfaCode)}
            requiresMfa={requiresMfa}
            onSubmit={signInWithEmail}
            isSubmitting={isSigningIn}
            submitButtonText={isSigningIn ? (t('login.loggingIn') || 'Signing in...') : (t('login.login') || 'Sign in')}
          />
        </div>
      )}

      {/* Help section - only show on first-time setup with default credentials and username/password auth allowed */}
      {isFirstTimeSetup && showDefaultCredentials && isUserPassAllowed && (
        <Alert
          color="blue"
          variant="light"
          radius="md"
          mt="xl"
        >
          <Stack gap="xs" align="center">
            <Text size="sm" fw={600} ta="center" style={{ color: 'var(--text-always-dark)' }}>
              {t('login.defaultCredentials', 'Default Login Credentials')}
            </Text>
            <Text size="sm" ta="center" style={{ color: 'var(--text-always-dark)' }}>
              <Text component="span" fw={600} style={{ color: 'var(--text-always-dark)' }}>{t('login.username', 'Username')}:</Text> admin
            </Text>
            <Text size="sm" ta="center" style={{ color: 'var(--text-always-dark)' }}>
              <Text component="span" fw={600} style={{ color: 'var(--text-always-dark)' }}>{t('login.password', 'Password')}:</Text> stirling
            </Text>
            <Text size="xs" ta="center" mt="xs" style={{ color: 'var(--text-always-dark-muted)' }}>
              {t('login.changePasswordWarning', 'Please change your password after logging in for the first time')}
            </Text>
          </Stack>
        </Alert>
      )}

    </AuthLayout>
  );
}
