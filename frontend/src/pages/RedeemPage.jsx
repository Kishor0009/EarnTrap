import { useEffect, useState, useCallback } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '../api/client'
import './RedeemPage.css'

export default function RedeemPage({ onLogout }) {
  const navigate = useNavigate()

  const [progress, setProgress] = useState(null)
  const [eligibility, setEligibility] = useState(null)
  const [history, setHistory] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [amount, setAmount] = useState('500')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState('')
  const [submitSuccess, setSubmitSuccess] = useState('')

  const loadData = useCallback(async (signal) => {
    setLoading(true)
    setError('')

    const [progResult, eligResult, histResult] = await Promise.allSettled([
      api.get('/progress/me', { signal }),
      api.get('/redemptions/eligibility', { signal }),
      api.get('/redemptions', { signal }),
    ])

    if (signal?.aborted) return

    // If any request failed with 401 Unauthorized, session expired -> kick to login
    const authFailed = [progResult, eligResult, histResult].some(
      r => r.status === 'rejected' && r.reason?.response?.status === 401,
    )
    if (authFailed) {
      localStorage.removeItem('earntrap_token')
      if (onLogout) onLogout(true)
      else navigate('/', { replace: true })
      return
    }

    if (progResult.status === 'fulfilled') {
      setProgress(progResult.value.data)
    }

    if (eligResult.status === 'fulfilled') {
      setEligibility(eligResult.value.data)
    }

    if (histResult.status === 'fulfilled') {
      setHistory(Array.isArray(histResult.value.data) ? histResult.value.data : [])
    }

    // Collect actual backend error messages if any API failed
    const failures = []
    if (progResult.status === 'rejected') {
      const err = progResult.reason
      const msg = err.response?.data?.message || err.response?.data?.detail || err.message || 'Could not load user progress.'
      failures.push(`Progress error: ${msg}`)
    }
    if (eligResult.status === 'rejected') {
      const err = eligResult.reason
      const msg = err.response?.data?.message || err.response?.data?.detail || err.message || 'Could not load redemption eligibility.'
      failures.push(`Eligibility error: ${msg}`)
    }

    if (failures.length > 0) {
      setError(failures.join(' | '))
    }

    setLoading(false)
  }, [navigate, onLogout])

  useEffect(() => {
    const controller = new AbortController()
    loadData(controller.signal)
    return () => controller.abort()
  }, [loadData])

  // Verified accounting values from backend
  const currentLevel = progress?.currentLevel ?? eligibility?.currentLevel ?? null
  const totalEarned = eligibility?.totalEarnedPoints ?? progress?.points ?? null
  const redeemablePoints = eligibility?.redeemablePoints ?? progress?.points ?? null

  const conversionRate = eligibility?.conversionRatePointsPerRupee || 10
  const eligibleAmountInRupees = eligibility?.eligibleAmountInRupees != null
    ? eligibility.eligibleAmountInRupees
    : redeemablePoints != null
    ? Math.floor(redeemablePoints / conversionRate)
    : null

  const levelRequirement = eligibility?.levelRequirement || 18
  const minRedemptionAmount = eligibility?.minRedemptionAmountInRupees || 500
  const minRedemptionPoints = eligibility?.minRedemptionPoints || (minRedemptionAmount * conversionRate) // 5000 points

  const isLevelEligible = currentLevel !== null ? currentLevel >= levelRequirement : false
  const availableEligiblePoints = Number(redeemablePoints ?? 0)
  const isBalanceEligible = redeemablePoints !== null ? availableEligiblePoints >= minRedemptionPoints : false

  // Redemption eligibility requires level 18 AND at least 5000 eligible points
  const isEligible = Boolean(
    eligibility
      ? eligibility.eligible
      : (isLevelEligible && isBalanceEligible)
  )

  // Calculator parsing & strict validation
  const rawAmount = String(amount).trim()
  const isPositiveInteger = /^[1-9]\d*$/.test(rawAmount)
  const numAmount = isPositiveInteger ? parseInt(rawAmount, 10) : 0
  const pointsRequired = numAmount * conversionRate
  const maxRedeemableAmount = eligibleAmountInRupees != null ? Math.max(0, eligibleAmountInRupees) : 0

  // Amount is only valid when user is eligible, amount >= 500, and points do not exceed available eligible points
  const isSufficientBalance = isEligible && pointsRequired <= availableEligiblePoints
  const isValidAmount = Boolean(
    isEligible &&
    isPositiveInteger &&
    numAmount >= minRedemptionAmount &&
    isSufficientBalance &&
    numAmount <= maxRedeemableAmount
  )

  // Live calculation results
  // remainingPoints = eligiblePoints - pointsRequired (when sufficient), else indicates shortage
  const remainingPoints = availableEligiblePoints >= pointsRequired
    ? availableEligiblePoints - pointsRequired
    : 0

  // Cash to receive MUST only be valid amount when eligible and verified
  const cashToReceive = isValidAmount ? numAmount : 0

  function handleAmountChange(e) {
    const val = e.target.value
    // Allow empty string to clear field, or digits only
    if (val === '' || /^\d+$/.test(val)) {
      setAmount(val)
      setSubmitError('')
      setSubmitSuccess('')
    }
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!isValidAmount || submitting) return

    setSubmitting(true)
    setSubmitError('')
    setSubmitSuccess('')

    try {
      const res = await api.post('/redemptions', {
        amountInRupees: numAmount,
      })

      const refCode = res.data?.referenceCode || 'RED-SUCCESS'
      setSubmitSuccess(
        `Redemption request for ₹${numAmount.toLocaleString('en-IN')} submitted successfully! Reference: ${refCode}. Status: PENDING verification.`
      )

      // Refresh progress, eligibility, and redemption history from backend
      await loadData()
    } catch (err) {
      const status = err.response?.status
      if (status === 401) {
        localStorage.removeItem('earntrap_token')
        if (onLogout) onLogout(true)
        else navigate('/', { replace: true })
        return
      }
      const data = err.response?.data
      const msg = typeof data === 'string' && data.trim()
        ? data
        : data?.message || data?.detail || 'Could not submit redemption request. Please check your balance and try again.'
      setSubmitError(msg)
    } finally {
      setSubmitting(false)
    }
  }

  function formatDate(isoStr) {
    if (!isoStr) return '—'
    try {
      const date = new Date(isoStr)
      return date.toLocaleDateString('en-IN', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return isoStr
    }
  }

  return (
    <main className="redeem-page">
      {/* ── header ── */}
      <header className="redeem-header">
        <button
          id="btn-back-dashboard"
          className="redeem-back"
          onClick={() => navigate('/')}
          aria-label="Return to dashboard"
        >
          ← Dashboard
        </button>

        <Link to="/" className="redeem-brand" aria-label="EarnTrap dashboard">
          <span className="redeem-brand-mark" aria-hidden="true">E</span>
          earntrap
        </Link>

        <div className="redeem-header-meta">
          <span className="redeem-badge">REDEEM</span>
        </div>
      </header>

      {/* ── main content ── */}
      <section className="redeem-content">
        <div className="redeem-intro">
          <span className="eyebrow">REWARD CASH OUT</span>
          <h1>Points Redemption</h1>
          <p>
            Convert your earned progression points into real rewards. Eligible from Level 18 with a ₹500 minimum.
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
            Loading redemption dashboard…
          </div>
        ) : (
          <>
            {/* ── A. Redemption Overview Stats ── */}
            <section className="redeem-stats-grid" aria-label="Redemption summary">
              <article className="redeem-stat-card featured">
                <div className="redeem-stat-top">
                  <span>Eligible Balance</span>
                  <span className="redeem-stat-icon">₹</span>
                </div>
                <strong className="redeem-stat-value">
                  {eligibleAmountInRupees !== null
                    ? `₹${Number(eligibleAmountInRupees).toLocaleString('en-IN')}`
                    : '—'}
                </strong>
                <small>
                  {redeemablePoints !== null
                    ? `${Number(redeemablePoints).toLocaleString('en-IN')} redeemable points`
                    : 'Balance unavailable'}
                </small>
              </article>

              <article className="redeem-stat-card">
                <div className="redeem-stat-top">
                  <span>Total Earned</span>
                  <span className="redeem-stat-icon">✳</span>
                </div>
                <strong className="redeem-stat-value">
                  {totalEarned !== null ? Number(totalEarned).toLocaleString('en-IN') : '—'}
                </strong>
                <small>
                  {totalEarned !== null ? 'Lifetime level points' : 'Points unavailable'}
                </small>
              </article>

              <article className="redeem-stat-card">
                <div className="redeem-stat-top">
                  <span>Current Level</span>
                  <span className="redeem-stat-icon">◇</span>
                </div>
                <strong className="redeem-stat-value">
                  {currentLevel !== null ? `Level ${currentLevel}` : '—'}
                </strong>
                <small>
                  {currentLevel !== null
                    ? (isLevelEligible
                      ? 'Level 18 unlocked ✓'
                      : `Requires Level 18 (${Math.max(0, levelRequirement - currentLevel)} to go)`)
                    : 'Requires Level 18'}
                </small>
              </article>

              <article className="redeem-stat-card">
                <div className="redeem-stat-top">
                  <span>Disbursed & Pending</span>
                  <span className="redeem-stat-icon">↗</span>
                </div>
                <strong className="redeem-stat-value">
                  {eligibility !== null
                    ? `₹${Number(eligibility.totalPaidAmountInRupees || 0).toLocaleString('en-IN')}`
                    : '—'}
                </strong>
                <small>
                  Pending: {eligibility !== null
                    ? `₹${Number(eligibility.totalPendingAmountInRupees || 0).toLocaleString('en-IN')}`
                    : '—'}
                </small>
              </article>
            </section>

            {/* ── B. Eligibility Status Card ── */}
            <section
              className={[
                'eligibility-card',
                isEligible
                  ? 'eligible'
                  : !isLevelEligible
                  ? 'locked-level'
                  : 'locked-balance',
              ].join(' ')}
              aria-label="Eligibility status"
            >
              <div className="eligibility-icon" aria-hidden="true">
                {isEligible ? '✓' : !isLevelEligible ? '🔒' : 'ℹ'}
              </div>
              <div className="eligibility-content">
                <h3>
                  {isEligible
                    ? 'You are eligible for redemption'
                    : !isLevelEligible
                    ? 'Redemption locked — Level 18 required'
                    : 'Level 18 reached — 5,000 points (₹500) required to redeem'}
                </h3>
                <p>
                  {eligibility?.message ||
                    (!isLevelEligible
                      ? `Redemption eligibility starts at Level ${levelRequirement}. You are currently at Level ${currentLevel ?? 1}.`
                      : `Minimum redemption amount is ₹${minRedemptionAmount} (${minRedemptionPoints.toLocaleString('en-IN')} points). You currently have ₹${eligibleAmountInRupees ?? 0} (${availableEligiblePoints.toLocaleString('en-IN')} points).`)}
                </p>

                {currentLevel !== null && !isLevelEligible && (
                  <div className="eligibility-progress-wrap">
                    <small>
                      Progress: Level {currentLevel} of {levelRequirement} (
                      {Math.max(0, levelRequirement - currentLevel)} levels to go)
                    </small>
                    <div className="eligibility-progress-bar">
                      <div
                        className="eligibility-progress-fill"
                        style={{
                          width: `${Math.min(100, Math.round((currentLevel / levelRequirement) * 100))}%`,
                        }}
                      />
                    </div>
                  </div>
                )}

                {isLevelEligible && !isBalanceEligible && (
                  <div className="eligibility-progress-wrap">
                    <small>
                      Points balance: {availableEligiblePoints.toLocaleString('en-IN')} / {minRedemptionPoints.toLocaleString('en-IN')} points ({Math.max(0, minRedemptionPoints - availableEligiblePoints).toLocaleString('en-IN')} points needed)
                    </small>
                    <div className="eligibility-progress-bar">
                      <div
                        className="eligibility-progress-fill"
                        style={{
                          width: `${Math.min(100, Math.round((availableEligiblePoints / minRedemptionPoints) * 100))}%`,
                        }}
                      />
                    </div>
                  </div>
                )}
              </div>
            </section>

            {/* ── C. Calculator & Request Form Layout ── */}
            <div className="redeem-form-layout">
              {/* Form */}
              <section className="redeem-card" aria-label="Redemption calculator">
                <span className="eyebrow">CONVERT & WITHDRAW</span>
                <h2>Redemption Calculator</h2>

                {submitSuccess && (
                  <div className="alert success-alert" role="status">
                    {submitSuccess}
                  </div>
                )}

                {submitError && (
                  <div className="alert error-alert" role="alert">
                    {submitError}
                  </div>
                )}

                <form onSubmit={handleSubmit} className="redeem-form" noValidate>
                  <div className="form-group">
                    <label htmlFor="redeem-amount">Amount in Rupees (₹)</label>
                    <div className="input-with-symbol">
                      <span className="input-symbol" aria-hidden="true">₹</span>
                      <input
                        id="redeem-amount"
                        type="text"
                        inputMode="numeric"
                        pattern="[0-9]*"
                        value={amount}
                        disabled={!isEligible || submitting}
                        onChange={handleAmountChange}
                        placeholder={isEligible ? '500' : 'Unavailable'}
                        aria-describedby="calc-helper-note"
                        required
                      />
                    </div>
                  </div>

                  {/* Preset Quick Chips (only available when account is eligible) */}
                  {isEligible && (
                    <div className="amount-chips">
                      <button
                        type="button"
                        className="amount-chip"
                        onClick={() => setAmount('500')}
                      >
                        ₹500 (Min)
                      </button>
                      <button
                        type="button"
                        className="amount-chip"
                        disabled={maxRedeemableAmount < 1000}
                        onClick={() => setAmount('1000')}
                      >
                        ₹1,000
                      </button>
                      <button
                        type="button"
                        className="amount-chip"
                        onClick={() => setAmount(String(maxRedeemableAmount))}
                      >
                        Max (₹{maxRedeemableAmount.toLocaleString('en-IN')})
                      </button>
                    </div>
                  )}

                  {/* Live Calculation Preview */}
                  <div className="calc-breakdown" aria-label="Calculation preview">
                    <div className="calc-row">
                      <span>Conversion rate:</span>
                      <strong>100 points = ₹10 (10 pts = ₹1)</strong>
                    </div>
                    <div className="calc-row">
                      <span>Points to deduct:</span>
                      <strong>
                        {numAmount > 0
                          ? `${pointsRequired.toLocaleString('en-IN')} points`
                          : '—'}
                      </strong>
                    </div>
                    <div className="calc-row">
                      <span>Remaining balance:</span>
                      <strong>
                        {!isEligible || pointsRequired > availableEligiblePoints
                          ? `${availableEligiblePoints.toLocaleString('en-IN')} points (Insufficient)`
                          : `${remainingPoints.toLocaleString('en-IN')} points (₹${Math.floor(remainingPoints / conversionRate).toLocaleString('en-IN')})`}
                      </strong>
                    </div>
                    <div className={`calc-row highlight ${!isValidAmount ? 'disabled-payout' : ''}`}>
                      <span>Total cash to receive:</span>
                      <strong>
                        {isValidAmount && cashToReceive > 0
                          ? `₹${cashToReceive.toLocaleString('en-IN')}`
                          : '₹0'}
                      </strong>
                    </div>
                  </div>

                  {/* Calculator Contextual Validation Note */}
                  <div id="calc-helper-note">
                    {!isLevelEligible ? (
                      <div className="calc-note warning">
                        🔒 Redemption is locked. Reaching Level 18 is required before reward points can be converted.
                      </div>
                    ) : !isBalanceEligible ? (
                      <div className="calc-note warning">
                        ℹ Minimum redemption is ₹500 (5,000 points). You have {availableEligiblePoints.toLocaleString('en-IN')} points ({Math.max(0, 5000 - availableEligiblePoints).toLocaleString('en-IN')} more needed).
                      </div>
                    ) : !rawAmount || numAmount === 0 ? (
                      <div className="calc-note info">
                        Enter an amount of ₹500 or more in whole rupees.
                      </div>
                    ) : !isPositiveInteger ? (
                      <div className="calc-note warning">
                        Please enter a valid whole rupee amount (no fractions or special characters).
                      </div>
                    ) : numAmount < minRedemptionAmount ? (
                      <div className="calc-note warning">
                        Minimum redemption amount is ₹{minRedemptionAmount.toLocaleString('en-IN')} (requires {minRedemptionPoints.toLocaleString('en-IN')} points).
                      </div>
                    ) : pointsRequired > availableEligiblePoints ? (
                      <div className="calc-note warning">
                        Requested ₹{numAmount.toLocaleString('en-IN')} requires {pointsRequired.toLocaleString('en-IN')} points, which exceeds your balance of {availableEligiblePoints.toLocaleString('en-IN')} points.
                      </div>
                    ) : (
                      <div className="calc-note info">
                        ✓ Eligible request: ₹{numAmount.toLocaleString('en-IN')} requires {pointsRequired.toLocaleString('en-IN')} points and leaves {remainingPoints.toLocaleString('en-IN')} points.
                      </div>
                    )}
                  </div>

                  <p className="verification-notice">
                    <strong>Payout notice:</strong> All redemption requests are subject to server validation,
                    compliance checks, and administrative disbursement. Live payment gateway integration is in
                    progress; requests remain in <code>PENDING</code> status until final disbursement.
                  </p>

                  <button
                    type="submit"
                    id="btn-submit-redemption"
                    className="primary-button"
                    disabled={!isValidAmount || submitting}
                  >
                    {submitting
                      ? 'Submitting request…'
                      : isValidAmount
                      ? `Request Redemption of ₹${cashToReceive.toLocaleString('en-IN')}`
                      : !isLevelEligible
                      ? 'Locked — Requires Level 18'
                      : !isBalanceEligible
                      ? 'Insufficient Points (5,000 Required)'
                      : pointsRequired > availableEligiblePoints
                      ? 'Exceeds Eligible Balance'
                      : 'Enter Valid Amount (Min ₹500)'}
                    {isValidAmount && !submitting && <span>→</span>}
                  </button>
                </form>
              </section>

              {/* Guidelines Card */}
              <aside className="guidelines-card" aria-label="Redemption guidelines">
                <h3>Redemption Guidelines</h3>

                <div className="guideline-item">
                  <div className="guideline-dot">1</div>
                  <div>
                    <strong>Level 18 Milestone</strong>
                    <p>Redemption unlocks after proving steady journey progression through Level 18.</p>
                  </div>
                </div>

                <div className="guideline-item">
                  <div className="guideline-dot">2</div>
                  <div>
                    <strong>₹500 Minimum</strong>
                    <p>Each request requires at least 5,000 points. Points are deducted atomically upon request submission.</p>
                  </div>
                </div>

                <div className="guideline-item">
                  <div className="guideline-dot">3</div>
                  <div>
                    <strong>Manual Review & Safety</strong>
                    <p>Requests are securely queued and verified to prevent duplicate deductions and fraudulent claims.</p>
                  </div>
                </div>

                <div className="guideline-item">
                  <div className="guideline-dot">4</div>
                  <div>
                    <strong>Points Restoration</strong>
                    <p>If a request is rejected during administrative verification, deducted points are automatically restored to your account.</p>
                  </div>
                </div>
              </aside>
            </div>

            {/* ── D. Redemption History ── */}
            <section className="history-section" aria-label="Redemption history">
              <div className="section-heading">
                <div>
                  <span className="eyebrow">YOUR TRANSACTIONS</span>
                  <h2>Redemption History</h2>
                </div>
              </div>

              <div className="history-card">
                {history.length === 0 ? (
                  <div className="empty-history">
                    <div className="empty-history-icon" aria-hidden="true">📄</div>
                    <h4>No redemption requests yet</h4>
                    <p>
                      When you reach Level 18 and submit your first points redemption, your requests and audit tracking will appear here.
                    </p>
                  </div>
                ) : (
                  <table className="history-table">
                    <thead>
                      <tr>
                        <th>Reference</th>
                        <th>Amount</th>
                        <th>Points</th>
                        <th>Date</th>
                        <th>Status</th>
                        <th>Transaction Ref</th>
                      </tr>
                    </thead>
                    <tbody>
                      {history.map(item => (
                        <tr key={item.id || item.referenceCode}>
                          <td>
                            <span className="history-ref">{item.referenceCode}</span>
                          </td>
                          <td>
                            <strong className="history-amount">
                              ₹{Number(item.amountInRupees || 0).toLocaleString('en-IN')}
                            </strong>
                          </td>
                          <td>
                            {Number(item.pointsRedeemed || 0).toLocaleString('en-IN')} pts
                          </td>
                          <td>{formatDate(item.createdAt)}</td>
                          <td>
                            <span
                              className={`status-pill status-${(item.status || 'pending').toLowerCase()}`}
                            >
                              {item.status === 'PENDING' && '◷ '}
                              {item.status === 'APPROVED' && '✓ '}
                              {item.status === 'PAID' && '✳ '}
                              {item.status === 'REJECTED' && '✕ '}
                              {item.status}
                            </span>
                          </td>
                          <td>
                            {item.transactionReference ? (
                              <code>{item.transactionReference}</code>
                            ) : (
                              <span style={{ color: '#9aa8a0', fontSize: '11px' }}>
                                {item.status === 'PENDING' ? 'Processing…' : '—'}
                              </span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </section>
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
