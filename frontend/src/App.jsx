
import { useEffect, useState, useCallback } from 'react'
import { Routes, Route, Navigate, useNavigate, Link } from 'react-router-dom'
import api from './api/client'
import LevelPage from './pages/LevelPage'
import ProfilePage from './pages/ProfilePage'
import RedeemPage from './pages/RedeemPage'
import TopUpPage from './pages/TopUpPage'

/* ─── helpers ─────────────────────────────────────────────────────────── */

const initialLogin    = { usernameOrEmail: '', password: '' }
const initialRegister = { fullName: '', username: '', email: '', phone: '', password: '' }

function getErrorMessage(error) {
  const data = error.response?.data
  if (typeof data === 'string' && data.trim()) return data
  if (data?.message)  return data.message
  if (data?.detail)   return data.detail
  if (!error.response) return 'Cannot reach the server. Check that Spring Boot is running on port 8080.'
  if (error.response.status === 401) return 'Your session has expired. Please sign in again.'
  if (error.response.status === 409) return 'An account with these details may already exist.'
  return 'Something went wrong. Please check your details and try again.'
}

function getList(data) {
  if (Array.isArray(data))          return data
  if (Array.isArray(data?.levels))  return data.levels
  if (Array.isArray(data?.content)) return data.content
  return []
}

/* ─── Auth screen ─────────────────────────────────────────────────────── */

function AuthScreen({ onLogin }) {
  const [mode, setMode]                 = useState('login')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading]           = useState(false)
  const [error, setError]               = useState('')
  const [notice, setNotice]             = useState('')
  const [login, setLogin]               = useState(initialLogin)
  const [register, setRegister]         = useState(initialRegister)

  function changeMode(next) {
    setMode(next)
    setError('')
    setNotice('')
    setShowPassword(false)
  }

  async function handleLogin(e) {
    e.preventDefault()
    setError('')
    setNotice('')
    setLoading(true)
    try {
      const res   = await api.post('/auth/login', {
        usernameOrEmail: login.usernameOrEmail.trim(),
        password:        login.password,
      })
      const token = res.data?.token
      if (!token) throw new Error('The login response did not contain a JWT token.')
      localStorage.setItem('earntrap_token', token)
      setLogin(initialLogin)
      onLogin()
    } catch (err) {
      setError(
        err.message?.includes('did not contain a JWT')
          ? err.message
          : getErrorMessage(err),
      )
    } finally {
      setLoading(false)
    }
  }

  async function handleRegister(e) {
    e.preventDefault()
    setError('')
    setNotice('')
    if (register.username.trim().length < 3) {
      setError('Username must contain at least 3 characters.')
      return
    }
    if (register.password.length < 6) {
      setError('Password must contain at least 6 characters.')
      return
    }
    setLoading(true)
    try {
      const payload = {
        fullName: register.fullName.trim(),
        username: register.username.trim(),
        email:    register.email.trim(),
        password: register.password,
      }
      if (register.phone.trim()) payload.phone = register.phone.trim()
      const res = await api.post('/auth/register', payload)
      setRegister(initialRegister)
      setLogin({ usernameOrEmail: payload.username, password: '' })
      setMode('login')
      setNotice(
        typeof res.data === 'string'
          ? res.data
          : res.data?.message || 'Account created successfully. Please sign in.',
      )
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="auth-layout">
      {/* ── brand panel ── */}
      <section className="brand-panel">
        <div className="brand">
          <div className="brand-mark">E</div>
          <span>earntrap</span>
        </div>

        <div className="brand-copy">
          <span className="eyebrow">YOUR JOURNEY STARTS HERE</span>
          <h1>
            Every level.<br />
            <span>One step forward.</span>
          </h1>
          <p>
            A new way to learn, progress through challenges, and track every
            milestone along the way.
          </p>
        </div>

        <div className="feature-card">
          <div className="feature-icon">↗</div>
          <div>
            <strong>Progress with purpose</strong>
            <p>Build your streak. Complete levels. Keep moving.</p>
          </div>
        </div>

        <div className="panel-footer">
          <span>LEARN · PROGRESS · ACHIEVE</span>
          <span>01 / 03</span>
        </div>
      </section>

      {/* ── form panel ── */}
      <section className="form-panel">
        <div className="mobile-brand">
          <div className="brand-mark">E</div>
          <span>earntrap</span>
        </div>

        <div className="auth-form-wrap">
          <div className="form-heading">
            <span className="eyebrow">
              {mode === 'login' ? 'WELCOME BACK' : 'GET STARTED'}
            </span>
            <h2>{mode === 'login' ? 'Sign in to EarnTrap' : 'Create your account'}</h2>
            <p>
              {mode === 'login'
                ? 'Pick up where your journey left off.'
                : 'Create an account and start your journey.'}
            </p>
          </div>

          <div className="auth-tabs">
            <button
              type="button"
              id="tab-login"
              className={mode === 'login' ? 'tab active' : 'tab'}
              onClick={() => changeMode('login')}
            >
              Sign in
            </button>
            <button
              type="button"
              id="tab-register"
              className={mode === 'register' ? 'tab active' : 'tab'}
              onClick={() => changeMode('register')}
            >
              Create account
            </button>
          </div>

          {notice && <div className="alert success-alert" role="status">{notice}</div>}
          {error  && <div className="alert error-alert"   role="alert">{error}</div>}

          {mode === 'login' ? (
            <form onSubmit={handleLogin} className="auth-form" noValidate>
              <label htmlFor="login-identity">Username or email</label>
              <input
                id="login-identity"
                type="text"
                autoComplete="username"
                placeholder="Enter your username or email"
                value={login.usernameOrEmail}
                onChange={e => setLogin({ ...login, usernameOrEmail: e.target.value })}
                required
              />

              <label htmlFor="login-password">Password</label>
              <div className="password-wrap">
                <input
                  id="login-password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="Enter your password"
                  value={login.password}
                  onChange={e => setLogin({ ...login, password: e.target.value })}
                  required
                />
                <button
                  type="button"
                  className="password-toggle"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  onClick={() => setShowPassword(s => !s)}
                >
                  {showPassword ? 'Hide' : 'Show'}
                </button>
              </div>

              <button id="btn-login" className="primary-button" disabled={loading}>
                {loading ? 'Signing in…' : 'Sign in'}
                {!loading && <span>→</span>}
              </button>
            </form>
          ) : (
            <form onSubmit={handleRegister} className="auth-form" noValidate>
              <label htmlFor="full-name">Full name</label>
              <input
                id="full-name"
                type="text"
                autoComplete="name"
                placeholder="Your full name"
                value={register.fullName}
                onChange={e => setRegister({ ...register, fullName: e.target.value })}
                required
              />

              <label htmlFor="register-username">Username</label>
              <input
                id="register-username"
                type="text"
                autoComplete="username"
                minLength={3}
                maxLength={30}
                placeholder="Choose a username"
                value={register.username}
                onChange={e => setRegister({ ...register, username: e.target.value })}
                required
              />

              <label htmlFor="register-email">Email address</label>
              <input
                id="register-email"
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                value={register.email}
                onChange={e => setRegister({ ...register, email: e.target.value })}
                required
              />

              <label htmlFor="register-phone">
                Phone number <span className="optional">(optional)</span>
              </label>
              <input
                id="register-phone"
                type="tel"
                autoComplete="tel"
                placeholder="Your phone number"
                value={register.phone}
                onChange={e => setRegister({ ...register, phone: e.target.value })}
              />

              <label htmlFor="register-password">Password</label>
              <div className="password-wrap">
                <input
                  id="register-password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  minLength={6}
                  placeholder="At least 6 characters"
                  value={register.password}
                  onChange={e => setRegister({ ...register, password: e.target.value })}
                  required
                />
                <button
                  type="button"
                  className="password-toggle"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  onClick={() => setShowPassword(s => !s)}
                >
                  {showPassword ? 'Hide' : 'Show'}
                </button>
              </div>

              <button id="btn-register" className="primary-button" disabled={loading}>
                {loading ? 'Creating account…' : 'Create account'}
                {!loading && <span>→</span>}
              </button>
            </form>
          )}

          <p className="form-note">
            By continuing, you agree to use EarnTrap responsibly and follow its
            terms and privacy policy.
          </p>
        </div>

        <footer className="form-footer">
          <span>© 2026 EarnTrap</span>
          <span>Built for your next milestone.</span>
        </footer>
      </section>
    </main>
  )
}

/* ─── Dashboard screen ────────────────────────────────────────────────── */

function DashboardScreen({ onLogout }) {
  const navigate = useNavigate()

  const [profile,          setProfile]          = useState(null)
  const [progress,         setProgress]          = useState(null)
  const [levels,           setLevels]            = useState([])
  const [dashboardLoading, setDashboardLoading]  = useState(false)
  const [dashboardError,   setDashboardError]    = useState('')
  const [selectedLevel,    setSelectedLevel]      = useState(null)

  const loadDashboard = useCallback(async (signal) => {
    setDashboardLoading(true)
    setDashboardError('')

    const [profileResult, progressResult, levelsResult] = await Promise.allSettled([
      api.get('/users/me',    { signal }),
      api.get('/progress/me', { signal }),
      api.get('/levels',      { signal }),
    ])

    if (signal?.aborted) return

    // If any call returns 401 the session is gone — kick to login
    const authFailed = [profileResult, progressResult, levelsResult].some(
      r => r.status === 'rejected' && r.reason?.response?.status === 401,
    )
    if (authFailed) {
      localStorage.removeItem('earntrap_token')
      onLogout(true)
      return
    }

    if (profileResult.status  === 'fulfilled') setProfile(profileResult.value.data)
    if (progressResult.status === 'fulfilled') setProgress(progressResult.value.data)
    if (levelsResult.status   === 'fulfilled') setLevels(getList(levelsResult.value.data))

    const failures = [profileResult, progressResult, levelsResult].filter(
      r => r.status === 'rejected',
    )
    if (failures.length) {
      setDashboardError(
        'Some dashboard data could not load. Check the backend endpoints and refresh.',
      )
    }

    setDashboardLoading(false)
  }, [onLogout])

  useEffect(() => {
    const controller = new AbortController()
    loadDashboard(controller.signal)
    return () => controller.abort()
  }, [loadDashboard])

  /* level card click
     • Available level 1-45  → navigate to its learning page
     • Completed             → read-only summary modal
     • Locked / Coming soon  → info modal (not playable)
  */
  function handleLevelClick(number, status, backendLevel) {
    if (status === 'Available' && number >= 1 && number <= 45) {
      navigate(`/level/${number}`)
      return
    }
    setSelectedLevel({
      number,
      status,
      title:       backendLevel?.title || `Level ${number}`,
      description: backendLevel?.description || '',
    })
  }

  return (
    <main className="dashboard-shell">
      {/* ── sidebar ── */}
      <aside className="sidebar">
        <Link className="brand dashboard-brand" to="/" aria-label="EarnTrap dashboard">
          <span className="brand-mark">E</span>
          <span>earntrap</span>
        </Link>

        <div className="sidebar-label">WORKSPACE</div>
        <Link className="sidebar-link active" to="/">
          <span>⌂</span> Overview
        </Link>
        <a className="sidebar-link" href="#journey">
          <span>◇</span> Level journey
        </a>
        <Link className="sidebar-link" to="/redeem">
          <span>₹</span> Redeem
        </Link>
        <Link className="sidebar-link" to="/top-up">
          <span>+</span> Top Up
        </Link>

        <div className="sidebar-bottom">
          <div className="sidebar-note">
            <span className="status-dot" aria-hidden="true" />
            <div>
              <strong>Account session</strong>
              <small>JWT authenticated</small>
            </div>
          </div>
          <button id="btn-logout" className="logout-button" onClick={() => onLogout(false)}>
            ↪ Log out
          </button>
        </div>
      </aside>

      {/* ── main content ── */}
      <section className="dashboard-main" id="dashboard">
        <header className="dashboard-topbar">
          <div>
            <div className="eyebrow">YOUR EARNTRAP SPACE</div>
            <h1>Dashboard</h1>
          </div>
          <button
            type="button"
            className="profile-chip"
            onClick={() => navigate('/profile')}
            aria-label="View profile"
          >
            <div className="avatar" aria-hidden="true">
              {(profile?.fullName || profile?.username || 'E').charAt(0).toUpperCase()}
            </div>
            <div>
              <strong>{profile?.fullName || profile?.username || 'EarnTrap user'}</strong>
              <small>{profile?.email || 'Signed in'}</small>
            </div>
          </button>
        </header>

        {dashboardError && (
          <div className="alert error-alert" role="alert">{dashboardError}</div>
        )}

        {dashboardLoading ? (
          <div className="dashboard-loading" role="status" aria-live="polite">
            <span className="loading-spinner" aria-hidden="true" />
            Loading your dashboard…
          </div>
        ) : (
          <>
            {/* Welcome banner */}
            <section className="welcome-banner" aria-label="Welcome message">
              <div>
                <span className="eyebrow">YOUR JOURNEY CONTINUES</span>
                <h2>Welcome back{profile?.fullName ? `, ${profile.fullName}` : ''}.</h2>
                <p>Small steps. Consistent progress. One level at a time.</p>
              </div>
              <div className="banner-symbol" aria-hidden="true">↗</div>
            </section>

            {/* Stats */}
            <section className="stats-grid" aria-label="Progress statistics">
              <article className="stat-card">
                <div className="stat-top">
                  <span>Current level</span>
                  <span className="stat-icon" aria-hidden="true">◇</span>
                </div>
                <strong className="stat-value">
                  {progress?.currentLevel ?? '—'}
                  <span className="stat-total"> / 60</span>
                </strong>
                <small>Your next milestone</small>
              </article>

              <article className="stat-card">
                <div className="stat-top">
                  <span>Total points</span>
                  <span className="stat-icon" aria-hidden="true">✳</span>
                </div>
                <strong className="stat-value">
                  {Number(progress?.points ?? 0).toLocaleString('en-IN')}
                </strong>
                <small>Earned from completed levels</small>
              </article>

              <article className="stat-card">
                <div className="stat-top">
                  <span>Journey progress</span>
                  <span className="stat-icon" aria-hidden="true">↗</span>
                </div>
                <strong className="stat-value">
                  {Math.round(
                    (Math.min(Math.max(Number(progress?.currentLevel ?? 1) - 1, 0), 60) / 60) * 100,
                  )}
                  <span className="stat-total">%</span>
                </strong>
                <div
                  className="progress-track"
                  role="progressbar"
                  aria-valuenow={Math.min(Math.max(Number(progress?.currentLevel ?? 1) - 1, 0), 60)}
                  aria-valuemin={0}
                  aria-valuemax={60}
                  aria-label="Journey progress"
                >
                  <div
                    className="progress-fill"
                    style={{
                      width: `${
                        (Math.min(Math.max(Number(progress?.currentLevel ?? 1) - 1, 0), 60) / 60) * 100
                      }%`,
                    }}
                  />
                </div>
              </article>
            </section>

            {/* Level journey */}
            <section className="journey-section" id="journey" aria-label="Level journey">
              <div className="section-heading">
                <div>
                  <span className="eyebrow">60 LEVELS · YOUR PACE</span>
                  <h2>Your level journey</h2>
                  <p>Complete your current level to move forward.</p>
                </div>
                <span className="level-count">Level {progress?.currentLevel ?? 1} of 60</span>
              </div>

              <div className="level-grid" role="list">
                {Array.from({ length: 60 }, (_, i) => {
                  const number     = i + 1
                  const current    = Number(progress?.currentLevel ?? 1)
                  const comingSoon = number === 46 && current > 45
                  const lockedHard = number >= 46          // 46-60 cannot be playable
                  const completed  = number < current && number <= 45
                  const unlocked   = number === current && !lockedHard
                  const locked     = !completed && !unlocked && !comingSoon

                  const backendLevel = levels.find(
                    item => Number(item.levelNumber ?? item.level ?? item.number) === number,
                  )

                  let status = 'Locked'
                  if (completed)  status = 'Completed'
                  else if (unlocked)   status = 'Available'
                  else if (comingSoon) status = 'Coming soon'

                  // Available level 1-45 → navigates. Everything else → modal.
                  const isClickable = status === 'Available' && number >= 1 && number <= 45
                  const ariaLabel   = `Level ${number} — ${status}${isClickable ? ', click to start' : ''}`

                  return (
                    <button
                      key={number}
                      type="button"
                      role="listitem"
                      className={[
                        'level-card',
                        completed  ? 'completed'   : '',
                        unlocked   ? 'unlocked'    : '',
                        locked     ? 'locked'      : '',
                        comingSoon ? 'coming-soon' : '',
                      ].filter(Boolean).join(' ')}
                      onClick={() => handleLevelClick(number, status, backendLevel)}
                      aria-label={ariaLabel}
                      aria-disabled={locked || comingSoon}
                    >
                      <span className="level-card-top" aria-hidden="true">
                        {completed  ? '✓'  :
                         comingSoon ? '◷'  :
                         locked     ? '🔒' : '↗'}
                      </span>
                      <strong>{number.toString().padStart(2, '0')}</strong>
                      <span className="level-status">{status}</span>
                    </button>
                  )
                })}
              </div>
            </section>


          </>
        )}

        <footer className="dashboard-footer">
          <span>© 2026 EarnTrap</span>
          <span>Learn · Progress · Achieve</span>
        </footer>
      </section>

      {/* ── Level info modal (locked / completed / coming-soon / non-navigable available) ── */}
      {selectedLevel && (
        <div
          className="modal-backdrop"
          onClick={() => setSelectedLevel(null)}
          role="presentation"
        >
          <section
            className="level-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="level-modal-title"
            onClick={e => e.stopPropagation()}
          >
            <button
              className="modal-close"
              onClick={() => setSelectedLevel(null)}
              aria-label="Close level details"
            >
              ×
            </button>
            <span className="eyebrow">EARNTRAP JOURNEY</span>
            <h2 id="level-modal-title">{selectedLevel.title}</h2>
            <span className="modal-status">{selectedLevel.status}</span>

            {selectedLevel.description && <p>{selectedLevel.description}</p>}

            {selectedLevel.status === 'Available' ? (
              <p>
                This is your current level. Click the level card to start
                learning.
              </p>
            ) : selectedLevel.status === 'Coming soon' ? (
              <p>This level is not available yet. Check back soon.</p>
            ) : selectedLevel.status === 'Locked' ? (
              <p>Complete the earlier levels to unlock this one.</p>
            ) : (
              <p>You have already progressed past this level. Great work!</p>
            )}

            <button
              className="primary-button"
              onClick={() => setSelectedLevel(null)}
            >
              Got it <span>→</span>
            </button>
          </section>
        </div>
      )}
    </main>
  )
}

/* ─── Root App with routing ───────────────────────────────────────────── */

export default function App() {
  // null = still checking, true = authenticated, false = not authenticated
  const [authState, setAuthState] = useState(null)

  // Validate existing token against the backend on startup.
  // If there is no token we skip the check and go straight to login.
  useEffect(() => {
    const token = localStorage.getItem('earntrap_token')
    if (!token) {
      setAuthState(false)
      return
    }

    let cancelled = false
    api.get('/users/me')
      .then(() => {
        if (!cancelled) setAuthState(true)
      })
      .catch(err => {
        if (cancelled) return
        const status = err.response?.status
        if (status === 401 || status === 403) {
          // Token is invalid or expired — clear it
          localStorage.removeItem('earntrap_token')
          setAuthState(false)
        } else {
          // Network / server error — optimistically trust stored token
          // so the user isn't logged out on a temporary backend outage
          setAuthState(true)
        }
      })
    return () => { cancelled = true }
  }, [])

  function handleLogin() {
    setAuthState(true)
  }

  function handleLogout(sessionExpired = false) {
    localStorage.removeItem('earntrap_token')
    setAuthState(false)
    if (sessionExpired) {
      // The Routes render will handle redirect; nothing extra needed here
      console.info('[EarnTrap] Session expired — redirected to login.')
    }
  }

  // While verifying the token show a minimal loading screen
  if (authState === null) {
    return (
      <div
        style={{
          minHeight:      '100vh',
          display:        'flex',
          alignItems:     'center',
          justifyContent: 'center',
          flexDirection:  'column',
          gap:            '16px',
          background:     '#f6f8f7',
          color:          '#607068',
          fontFamily:     "'DM Sans', system-ui, sans-serif",
        }}
        role="status"
        aria-live="polite"
        aria-label="Verifying your session"
      >
        <div
          style={{
            display:        'grid',
            placeItems:     'center',
            width:          '48px',
            height:         '48px',
            borderRadius:   '14px',
            background:     '#137a47',
            color:          '#fff',
            fontSize:       '24px',
            fontWeight:     800,
            marginBottom:   '4px',
          }}
        >
          E
        </div>
        <span className="loading-spinner" aria-hidden="true" />
        <span style={{ fontSize: '13px' }}>Verifying your session…</span>
      </div>
    )
  }

  return (
    <Routes>
      {/* Root — auth or dashboard */}
      <Route
        path="/"
        element={
          authState
            ? <DashboardScreen onLogout={handleLogout} />
            : <AuthScreen      onLogin={handleLogin}   />
        }
      />

      {/* Profile page — protected */}
      <Route
        path="/profile"
        element={
          authState
            ? <ProfilePage onLogout={handleLogout} />
            : <Navigate to="/" replace />
        }
      />

      {/* Redeem page — protected */}
      <Route
        path="/redeem"
        element={
          authState
            ? <RedeemPage onLogout={handleLogout} />
            : <Navigate to="/" replace />
        }
      />

      {/* Top up page — protected */}
      <Route
        path="/top-up"
        element={
          authState
            ? <TopUpPage onLogout={handleLogout} />
            : <Navigate to="/" replace />
        }
      />

      {/* Level pages — protected, dynamic route covers levels 1-45 */}
      <Route
        path="/level/:levelNumber"
        element={
          authState
            ? <LevelPage onLogout={handleLogout} />
            : <Navigate to="/" replace />
        }
      />

      {/* Catch-all — redirect to root */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
