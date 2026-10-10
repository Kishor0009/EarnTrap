
import { useEffect, useRef, useState, useCallback } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import api from '../api/client'
import './LevelPage.css'

/* ─── constants ────────────────────────────────────────────────────────── */

/**
 * Public sample video used only when the backend has not configured a
 * videoUrl for this level. Set a videoUrl on the Level entity — no
 * frontend change required.
 */
const FALLBACK_VIDEO_URL =
  'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4'

/* ─── guard: parse and validate the URL level number ─────────────────── */
function parseLevelNumber(raw) {
  const n = Number(raw)
  // Only levels 1-45 have completable learning pages per product rules
  if (!Number.isInteger(n) || n < 1 || n > 45) return null
  return n
}

/* ─── LevelPage ────────────────────────────────────────────────────────── */

export default function LevelPage({ onLogout }) {
  const { levelNumber: rawParam } = useParams()
  const navigate = useNavigate()
  const videoRef = useRef(null)

  const levelNumber = parseLevelNumber(rawParam)

  // Level metadata from GET /api/levels
  const [levelData,    setLevelData]    = useState(null)
  const [levelError,   setLevelError]   = useState('')
  const [dataLoading,  setDataLoading]  = useState(true)

  // Progress from GET /api/progress/me
  const [progress,      setProgress]      = useState(null)
  const [progressError, setProgressError] = useState('')

  // Video / completion state
  const [videoEnded,  setVideoEnded]  = useState(false)
  const [completing,  setCompleting]  = useState(false)
  const [completed,   setCompleted]   = useState(false)  // confirmed done this session
  const [compMessage, setCompMessage] = useState('')
  const [compError,   setCompError]   = useState('')

  // Redirect invalid level numbers immediately
  useEffect(() => {
    if (levelNumber === null) {
      navigate('/', { replace: true })
    }
  }, [levelNumber, navigate])

  /* ── load level details + current progress in parallel ── */
  const fetchInitialData = useCallback(async (signal) => {
    if (!levelNumber) return
    setDataLoading(true)
    setLevelError('')
    setProgressError('')

    const [levelsResult, progressResult] = await Promise.allSettled([
      api.get('/levels',      { signal }),
      api.get('/progress/me', { signal }),
    ])

    if (signal?.aborted) return

    // Auth failures → clear token and redirect to login
    const authFailed = [levelsResult, progressResult].some(
      r => r.status === 'rejected' && r.reason?.response?.status === 401,
    )
    if (authFailed) {
      localStorage.removeItem('earntrap_token')
      if (onLogout) onLogout(true)
      else navigate('/', { replace: true })
      return
    }

    if (levelsResult.status === 'fulfilled') {
      const raw = levelsResult.value.data
      const arr = Array.isArray(raw)
        ? raw
        : Array.isArray(raw?.levels)  ? raw.levels
        : Array.isArray(raw?.content) ? raw.content
        : []
      const found = arr.find(
        l => Number(l.levelNumber ?? l.level ?? l.number) === levelNumber,
      )
      setLevelData(found ?? null)
    } else {
      setLevelError('Could not load level details. The video will still play.')
    }

    if (progressResult.status === 'fulfilled') {
      const prog = progressResult.value.data

      // If the user's current level is past this one, they've already completed it.
      // Mark completed so the button reflects the correct state without waiting for
      // the user to watch the video again.
      if (prog.currentLevel > levelNumber) {
        setCompleted(true)
      }

      setProgress(prog)
    } else {
      setProgressError('Could not load your current progress.')
    }

    setDataLoading(false)
  }, [levelNumber, navigate, onLogout])

  useEffect(() => {
    const controller = new AbortController()
    fetchInitialData(controller.signal)
    return () => controller.abort()
  }, [fetchInitialData])

  /* ── refresh progress after a completion ── */
  async function refreshProgress() {
    try {
      const res = await api.get('/progress/me')
      setProgress(res.data)
    } catch {
      // Non-critical — sidebar values just won't update immediately
    }
  }

  /* ── complete level ── */
  async function handleComplete() {
    if (!videoEnded || completing || completed) return

    setCompleting(true)
    setCompError('')
    setCompMessage('')

    try {
      const res    = await api.post(`/levels/${levelNumber}/complete`)
      const result = res.data  // LevelCompletionResponse

      if (result.alreadyCompleted) {
        // Backend confirms: already done, no points awarded
        setCompMessage(
          result.message ||
          `Level ${levelNumber} was already completed. No duplicate points were awarded.`,
        )
        // Mark locally so the button disables and the UI is consistent
        setCompleted(true)
      } else {
        // Backend confirmed the completion and awarded points
        const pts = result.pointsAwarded ?? 0
        const total = result.totalPoints != null
          ? ` Your total: ${Number(result.totalPoints).toLocaleString('en-IN')} points.`
          : ''
        setCompMessage(
          result.message ||
          `Level ${levelNumber} complete! +${pts} points awarded.${total}`,
        )
        setCompleted(true)
      }

      // Always refresh from backend — never increment locally
      await refreshProgress()
    } catch (err) {
      const status = err.response?.status
      if (status === 401 || status === 403) {
        localStorage.removeItem('earntrap_token')
        if (onLogout) onLogout(true)
        else navigate('/', { replace: true })
        return
      }

      // 409 Conflict = backend enforces sequential completion:
      // e.g. user tries to complete a level they haven't reached yet
      const errData = err.response?.data
      setCompError(
        errData?.message ||
        errData?.detail  ||
        (typeof errData === 'string' && errData.trim()
          ? errData
          : `Could not complete Level ${levelNumber}. Please try again.`),
      )
    } finally {
      setCompleting(false)
    }
  }

  /* ── if level number is invalid, render nothing while redirect fires ── */
  if (levelNumber === null) return null

  /* ── derived values ── */
  const videoUrl        = levelData?.videoUrl?.trim() || FALLBACK_VIDEO_URL
  const title           = levelData?.title || `Level ${levelNumber}`
  const isUsingFallback = !levelData?.videoUrl?.trim()

  // Determine if this level is the user's current one (completable)
  const userCurrentLevel  = progress?.currentLevel ?? null
  const isCurrentLevel    = userCurrentLevel === levelNumber
  const isAlreadyPast     = userCurrentLevel != null && userCurrentLevel > levelNumber
  // completed state covers: already-past AND confirmed-this-session
  const isEffectivelyDone = completed || isAlreadyPast

  /* ── button label ── */
  let buttonLabel = 'Watch the video to continue'
  if (dataLoading)           buttonLabel = 'Loading…'
  else if (completing)       buttonLabel = 'Completing level…'
  else if (isEffectivelyDone) buttonLabel = '✓ Level completed'
  else if (videoEnded)       buttonLabel = `Complete Level ${levelNumber} →`

  /* ── step states ── */
  const step1Done = videoEnded || isEffectivelyDone
  const step2Done = !!compMessage || isAlreadyPast

  const padded = String(levelNumber).padStart(2, '0')

  return (
    <main className="level-page">
      {/* ── header ── */}
      <header className="level-header">
        <button
          id="btn-back-dashboard"
          className="level-back"
          onClick={() => navigate('/')}
          aria-label="Return to dashboard"
        >
          ← Dashboard
        </button>

        <div className="level-brand" aria-label="EarnTrap">
          <span className="level-brand-mark" aria-hidden="true">E</span>
          earntrap
        </div>

        <span className="level-header-label" aria-label={`Level ${levelNumber} of 60`}>
          LEVEL {padded} / 60
        </span>
      </header>

      {/* ── content ── */}
      <section className="level-content">
        <div className="level-intro">
          <span className="level-eyebrow">
            {isAlreadyPast ? 'ALREADY COMPLETED' : 'YOUR JOURNEY CONTINUES'}
          </span>
          <h1>
            Level {padded} <span>— {title}</span>
          </h1>
          <p>
            {isAlreadyPast
              ? 'You have already completed this level. Watch the video again to review the content.'
              : 'Watch the lesson video in full, then click the completion button to earn your points and unlock the next level.'}
          </p>
        </div>

        {/* ── loading overlay ── */}
        {dataLoading && (
          <div className="dashboard-loading" role="status" aria-live="polite">
            <span className="loading-spinner" aria-hidden="true" />
            Loading level {levelNumber}…
          </div>
        )}

        {!dataLoading && (
          <>
            {/* ── lesson card ── */}
            <section className="lesson-card" aria-label="Lesson content">
              <div className="lesson-topline">
                <span className="lesson-tag">VIDEO LESSON</span>
                <span className="lesson-duration">
                  {isUsingFallback ? 'Sample video' : 'Lesson video'}
                </span>
              </div>

              {levelError && (
                <div className="level-alert level-alert-warn" role="alert">
                  {levelError}
                </div>
              )}

              {/* Warning when user's current level doesn't match (sequential enforcement) */}
              {!isAlreadyPast && !isCurrentLevel && userCurrentLevel !== null && (
                <div className="level-alert level-alert-warn" role="note">
                  You are currently on Level {userCurrentLevel}. You must complete
                  earlier levels before this one can be unlocked.
                </div>
              )}

              {/* Video player */}
              <video
                ref={videoRef}
                className="lesson-video"
                controls
                playsInline
                preload="metadata"
                src={videoUrl}
                aria-label={`Level ${levelNumber} lesson video`}
                onEnded={() => setVideoEnded(true)}
                onPlay={() => {
                  // Allow replay: reset client-side gate (not the server-confirmed state)
                  setVideoEnded(false)
                  if (!isEffectivelyDone) {
                    setCompMessage('')
                    setCompError('')
                  }
                }}
              >
                Your browser does not support video playback.
              </video>

              {/* Level details + reward preview */}
              <div className="lesson-details">
                <div>
                  <span className="level-eyebrow">LEVEL {padded}</span>
                  <h2>{title}</h2>
                  <p>
                    {isUsingFallback
                      ? 'A sample video is shown while the real lesson is being prepared. Set a videoUrl on the Level entity to replace it — no frontend change required.'
                      : 'Complete the lesson and pass each milestone to continue your journey.'}
                  </p>
                </div>

                <div className="reward-preview" aria-label="Level reward">
                  <span>LEVEL REWARD</span>
                  <strong>+100 points</strong>
                </div>
              </div>

              {/* Step indicators */}
              <div className="lesson-progress" aria-label="Lesson steps">
                <span
                  className={step1Done ? 'lesson-step done' : 'lesson-step active'}
                  aria-label={step1Done ? 'Step 1 complete: video watched' : 'Step 1: watch video'}
                >
                  <span aria-hidden="true">1</span>
                  Watch video
                </span>
                <span className="lesson-step-separator" aria-hidden="true" />
                <span
                  className={step2Done ? 'lesson-step done' : 'lesson-step'}
                  aria-label={step2Done ? 'Step 2 complete: level completed' : 'Step 2: complete level'}
                >
                  <span aria-hidden="true">2</span>
                  Complete level
                </span>
              </div>

              {/* Alerts */}
              {compError && (
                <div className="level-alert level-alert-error" role="alert">
                  {compError}
                </div>
              )}

              {compMessage && (
                <div className="level-alert level-alert-success" role="status">
                  {compMessage}
                </div>
              )}

              {isAlreadyPast && !compMessage && (
                <div className="level-alert level-alert-success" role="status">
                  You have already completed Level {levelNumber} and earned 100
                  points. No duplicate points are awarded.
                </div>
              )}

              {/* Completion button */}
              <button
                id="btn-complete-level"
                className="level-complete-button"
                disabled={!videoEnded || completing || isEffectivelyDone}
                onClick={handleComplete}
                aria-label={buttonLabel}
              >
                {buttonLabel}
              </button>

              {/* Dev-only disclaimer — not production-grade security */}
              <div className="level-security-note" role="note">
                <strong>Development note:</strong>{' '}
                The button above is enabled by the browser's{' '}
                <code>video.ended</code> event. This is a basic development gate
                only — it is <em>not</em> secure verification. Any user can call{' '}
                <code>POST /api/levels/{levelNumber}/complete</code> directly
                without watching the video. Production deployments should issue a
                short-lived, server-side watch token after a verified video
                completion event and require it in the completion request.
              </div>

              <p className="level-footnote">
                Points are virtual progress points within EarnTrap. Completing a
                level does not itself guarantee any cash withdrawal.
              </p>
            </section>

            {/* ── progress summary ── */}
            <section className="level-progress-summary" aria-label="Your current progress">
              {progressError && (
                <div
                  className="level-alert level-alert-warn"
                  style={{ gridColumn: '1 / -1' }}
                  role="alert"
                >
                  {progressError}
                </div>
              )}

              <div>
                <span>Current level</span>
                <strong>{progress?.currentLevel ?? '—'} / 60</strong>
              </div>
              <div>
                <span>Total points</span>
                <strong>
                  {progress?.points != null
                    ? Number(progress.points).toLocaleString('en-IN')
                    : '—'}
                </strong>
              </div>
              <div>
                <span>Turbo Pass</span>
                <strong>{progress?.turboPassActive ? 'Active' : 'Inactive'}</strong>
              </div>
            </section>
          </>
        )}
      </section>
    </main>
  )
}
