import { useEffect, useState, useCallback } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '../api/client'
import './ProfilePage.css'

export default function ProfilePage({ onLogout }) {
  const navigate = useNavigate()
  const [profile, setProfile] = useState(null)
  const [progress, setProgress] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadProfile = useCallback(async (signal) => {
    setLoading(true)
    setError('')

    const [profileResult, progressResult] = await Promise.allSettled([
      api.get('/users/me', { signal }),
      api.get('/progress/me', { signal }),
    ])

    if (signal?.aborted) return

    // If any call returns 401 the session is invalid or expired — kick to login
    const authFailed = [profileResult, progressResult].some(
      r => r.status === 'rejected' && r.reason?.response?.status === 401,
    )
    if (authFailed) {
      localStorage.removeItem('earntrap_token')
      if (onLogout) onLogout(true)
      else navigate('/', { replace: true })
      return
    }

    if (profileResult.status === 'fulfilled') {
      setProfile(profileResult.value.data)
    }
    if (progressResult.status === 'fulfilled') {
      setProgress(progressResult.value.data)
    }

    if (profileResult.status === 'rejected' && progressResult.status === 'rejected') {
      setError('Could not load profile details. Please refresh or try again.')
    }

    setLoading(false)
  }, [navigate, onLogout])

  useEffect(() => {
    const controller = new AbortController()
    loadProfile(controller.signal)
    return () => controller.abort()
  }, [loadProfile])

  const initial = (profile?.fullName || profile?.username || 'E').charAt(0).toUpperCase()

  return (
    <main className="profile-page">
      {/* ── header ── */}
      <header className="profile-header">
        <button
          id="btn-back-dashboard"
          className="profile-back"
          onClick={() => navigate('/')}
          aria-label="Return to dashboard"
        >
          ← Dashboard
        </button>

        <Link to="/" className="profile-brand" aria-label="EarnTrap dashboard">
          <span className="profile-brand-mark" aria-hidden="true">E</span>
          earntrap
        </Link>

        <div className="profile-header-meta">
          <span className="profile-badge">PROFILE</span>
        </div>
      </header>

      {/* ── main content ── */}
      <section className="profile-content">
        <div className="profile-intro">
          <span className="eyebrow">YOUR DETAILS</span>
          <h1>My Profile</h1>
          <p>
            Review and manage your personal account information and EarnTrap status.
          </p>
        </div>

        {error && (
          <div className="alert error-alert" role="alert">
            {error}
          </div>
        )}

        {loading ? (
          <div className="dashboard-loading" role="status" aria-live="polite">
            <span className="loading-spinner" aria-hidden="true" />
            Loading your profile…
          </div>
        ) : (
          <>
            {/* User overview banner */}
            <section className="profile-banner" aria-label="User summary">
              <div className="profile-banner-avatar" aria-hidden="true">
                {initial}
              </div>
              <div className="profile-banner-info">
                <h2>{profile?.fullName || profile?.username || 'EarnTrap user'}</h2>
                <div className="profile-banner-tags">
                  <span className="profile-tag">@{profile?.username || 'user'}</span>
                  <span className="profile-tag status-tag">
                    {profile?.status || 'Active'}
                  </span>
                  <span className={`profile-tag ${progress?.turboPassActive ? 'turbo-tag active' : 'turbo-tag'}`}>
                    Turbo Pass: {progress?.turboPassActive ? 'Active' : 'Not active'}
                  </span>
                </div>
              </div>
            </section>

            {/* Account Information Card */}
            <section className="profile-details-section" aria-label="Account details">
              <div className="section-heading">
                <div>
                  <span className="eyebrow">ACCOUNT INFORMATION</span>
                  <h2>Personal details</h2>
                </div>
              </div>

              <div className="account-card">
                <div>
                  <span>Full name</span>
                  <strong>{profile?.fullName || 'Not provided'}</strong>
                </div>
                <div>
                  <span>Username</span>
                  <strong>{profile?.username || 'Not provided'}</strong>
                </div>
                <div>
                  <span>Email</span>
                  <strong>{profile?.email || 'Not provided'}</strong>
                </div>
                <div>
                  <span>Phone</span>
                  <strong>{profile?.phone || 'Not provided'}</strong>
                </div>
                <div>
                  <span>Turbo Pass</span>
                  <strong>{progress?.turboPassActive ? 'Active' : 'Not active'}</strong>
                </div>
                <div>
                  <span>Account status</span>
                  <strong>{profile?.status || 'Active'}</strong>
                </div>
              </div>
            </section>

            {/* Actions */}
            {onLogout && (
              <div className="profile-actions">
                <button
                  type="button"
                  className="profile-action-btn secondary"
                  onClick={() => onLogout(false)}
                >
                  ↪ Log out
                </button>
              </div>
            )}
          </>
        )}
      </section>

      <footer className="profile-footer">
        <span>© 2026 EarnTrap</span>
        <span>Learn · Progress · Achieve</span>
      </footer>
    </main>
  )
}
