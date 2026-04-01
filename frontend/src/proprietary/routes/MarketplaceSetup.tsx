import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PasswordInput, Button, Text, Progress } from '@mantine/core';
import { useDocumentMeta } from '@app/hooks/useDocumentMeta';
import AuthLayout from '@app/routes/authShared/AuthLayout';
import LoginHeader from '@app/routes/login/LoginHeader';
import ErrorMessage from '@app/routes/login/ErrorMessage';
import apiClient from '@app/services/apiClient';
import { BASE_PATH } from '@app/constants/app';
import '@app/routes/authShared/auth.css';

// Force light mode styles for auth inputs
const authInputStyles = {
  input: {
    backgroundColor: 'var(--auth-input-bg-light-only)',
    color: 'var(--auth-input-text-light-only)',
    borderColor: 'var(--auth-input-border-light-only)',
    '&:focus': {
      borderColor: 'var(--auth-border-focus-light-only)',
    },
  },
  label: {
    color: 'var(--auth-label-text-light-only)',
  },
};

interface SetupStatusResponse {
  needsSetup: boolean;
  email: string;
  planId: string;
}

interface SetupAccountResponse {
  status: string;
  message: string;
  email: string;
  planId: string;
}

function getPasswordStrength(password: string): number {
  let strength = 0;
  if (password.length >= 8) strength += 25;
  if (password.length >= 12) strength += 15;
  if (/[a-z]/.test(password)) strength += 15;
  if (/[A-Z]/.test(password)) strength += 15;
  if (/[0-9]/.test(password)) strength += 15;
  if (/[^a-zA-Z0-9]/.test(password)) strength += 15;
  return Math.min(100, strength);
}

function getStrengthColor(strength: number): string {
  if (strength < 30) return 'red';
  if (strength < 60) return 'orange';
  if (strength < 80) return 'yellow';
  return 'green';
}

export default function MarketplaceSetup() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { t } = useTranslation();

  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [email, setEmail] = useState('');
  const [planId, setPlanId] = useState('');

  const subscriptionId = searchParams.get('subscription');
  const emailParam = searchParams.get('email');

  const baseUrl = window.location.origin + BASE_PATH;

  useDocumentMeta({
    title: `${t('marketplace.setupAccount', 'Set Up Your Account')} - PaperBolt`,
    description: t('marketplace.setupDescription', 'Create your password to activate your Azure Marketplace subscription'),
    ogTitle: `${t('marketplace.setupAccount', 'Set Up Your Account')} - PaperBolt`,
    ogDescription: t('marketplace.setupDescription', 'Create your password to activate your Azure Marketplace subscription'),
    ogImage: `${baseUrl}/og_images/home.png`,
    ogUrl: `${window.location.origin}${window.location.pathname}`
  });

  // Check if setup is needed
  useEffect(() => {
    const checkSetupStatus = async () => {
      if (!subscriptionId || !emailParam) {
        setError(t('marketplace.missingParams', 'Missing subscription or email information. Please use the link from your Azure Marketplace purchase.'));
        setIsLoading(false);
        return;
      }

      try {
        const response = await apiClient.get<SetupStatusResponse>('/api/v1/marketplace/setup-status', {
          params: {
            subscriptionId,
            email: emailParam
          }
        });

        const data = response.data;

        if (!data.needsSetup) {
          // User already has an account, redirect to login
          navigate(`/login?marketplace=1&subscription=${subscriptionId}&email=${encodeURIComponent(emailParam)}&messageType=accountExists`, { replace: true });
          return;
        }

        setEmail(data.email);
        setPlanId(data.planId);
        setIsLoading(false);
      } catch (err: unknown) {
        console.error('[MarketplaceSetup] Error checking setup status:', err);
        const errorMessage = err instanceof Error ? err.message : 'Failed to verify subscription';
        setError(errorMessage);
        setIsLoading(false);
      }
    };

    checkSetupStatus();
  }, [subscriptionId, emailParam, navigate, t]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    // Validation
    if (password.length < 8) {
      setError(t('marketplace.passwordTooShort', 'Password must be at least 8 characters'));
      return;
    }

    if (password !== confirmPassword) {
      setError(t('marketplace.passwordMismatch', 'Passwords do not match'));
      return;
    }

    setIsSubmitting(true);

    try {
      const response = await apiClient.post<SetupAccountResponse>('/api/v1/marketplace/setup-account', {
        subscriptionId,
        email,
        password
      });

      if (response.data.status === 'success') {
        // Redirect to login with success message
        navigate(`/login?marketplace=1&email=${encodeURIComponent(email)}&messageType=accountCreated`, { replace: true });
      } else {
        setError(response.data.message || t('marketplace.setupFailed', 'Failed to create account'));
      }
    } catch (err: unknown) {
      console.error('[MarketplaceSetup] Error setting up account:', err);
      const errorMessage = err instanceof Error ? err.message : 'Failed to create account';
      setError(errorMessage);
    } finally {
      setIsSubmitting(false);
    }
  };

  const passwordStrength = getPasswordStrength(password);
  const strengthColor = getStrengthColor(passwordStrength);

  if (isLoading) {
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
          {t('marketplace.planInfo', 'Plan: {{plan}}', { plan: planId.charAt(0).toUpperCase() + planId.slice(1) })}
        </p>
      </div>

      <ErrorMessage error={error} />

      <form onSubmit={handleSubmit}>
        <div className="auth-fields">
          {/* Email display (read-only) */}
          <div className="auth-field">
            <label className="auth-label">{t('login.email', 'Email')}</label>
            <div style={{
              padding: '0.625rem 0.75rem',
              backgroundColor: 'var(--auth-input-bg-light-only)',
              border: '1px solid var(--auth-input-border-light-only)',
              borderRadius: '0.625rem',
              fontSize: '0.875rem',
              color: 'var(--auth-input-text-light-only)',
              opacity: 0.8
            }}>
              {email}
            </div>
          </div>

          {/* Password field */}
          <div className="auth-field">
            <PasswordInput
              id="password"
              label={t('marketplace.newPassword', 'New Password')}
              name="new-password"
              autoComplete="new-password"
              placeholder={t('marketplace.enterPassword', 'Enter your password')}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              classNames={{ label: 'auth-label' }}
              styles={authInputStyles}
              autoFocus
            />
            {password && (
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
              error={confirmPassword && password !== confirmPassword ? t('marketplace.passwordMismatch', 'Passwords do not match') : undefined}
              classNames={{ label: 'auth-label' }}
              styles={authInputStyles}
            />
          </div>
        </div>

        <Button
          type="submit"
          disabled={isSubmitting || !password || !confirmPassword || password !== confirmPassword || password.length < 8}
          className="auth-button auth-cta-button"
          fullWidth
          loading={isSubmitting}
        >
          {isSubmitting 
            ? t('marketplace.creatingAccount', 'Creating account...') 
            : t('marketplace.createAccount', 'Create Account & Continue')}
        </Button>
      </form>

      {/* Already have an account link */}
      <div className="auth-bottom-right">
        <button
          type="button"
          onClick={() => navigate(`/login?marketplace=1&subscription=${subscriptionId}&email=${encodeURIComponent(email)}`)}
          className="auth-link-black"
        >
          {t('marketplace.alreadyHaveAccount', 'Already have an account? Sign in')}
        </button>
      </div>
    </AuthLayout>
  );
}
