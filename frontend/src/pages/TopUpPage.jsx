import { useEffect, useState, useCallback } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '../api/client'
import './TopUpPage.css'

const PRESET_AMOUNTS = [100, 250, 500, 1000]

export default function TopUpPage({ onLogout }) {
  const navigate = useNavigate()

  const [wallet, setWallet] = useState(null)
  const [transactions, setTransactions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  // Amount state
  const [amount, setAmount] = useState('500')
  const [submitting, setSubmitting] = useState(false)
  const [orderError, setOrderError] = useState('')
  const [orderSuccess, setOrderSuccess] = useState('')

  // Sandbox simulation modal state
  const [activeOrder, setActiveOrder] = useState(null)
  const [verifyingOutcome, setVerifyingOutcome] = useState('')

  const loadData = useCallback(async (signal) => {
    setLoading(true)
    setError('')

    const [walletRes, txRes] = await Promise.allSettled([
      api.get('/wallet/me', { signal }),
      api.get('/wallet/transactions', { signal }),
    ])

    if (signal?.aborted) return

    // If any request failed with 401 Unauthorized, session expired -> kick to login
    const authFailed = [walletRes, txRes].some(
      r => r.status === 'rejected' && r.reason?.response?.status === 401,
    )
    if (authFailed) {
      localStorage.removeItem('earntrap_token')
      if (onLogout) onLogout(true)
      else navigate('/', { replace: true })
      return
    }

    if (walletRes.status === 'fulfilled') {
      setWallet(walletRes.value.data)
    }

    if (txRes.status === 'fulfilled') {
      setTransactions(Array.isArray(txRes.value.data) ? txRes.value.data : [])
    }

    const failures = []
    if (walletRes.status === 'rejected') {
      const err = walletRes.reason
      failures.push(err.response?.data?.message || err.response?.data?.detail || 'Could not load wallet balance.')
    }
    if (txRes.status === 'rejected') {
      const err = txRes.reason
      console.warn('Could not load transaction history:', err)
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

  // Amount validation
  const rawAmount = String(amount).trim()
  const isPositiveInteger = /^[1-9]\d*$/.test(rawAmount)
  const numAmount = isPositiveInteger ? parseInt(rawAmount, 10) : 0
  const isValidAmount = isPositiveInteger && numAmount >= 100 && numAmount <= 100000

  function handleAmountChange(e) {
    const val = e.target.value
    // Allow empty string to clear field or digits only
    if (val === '' || /^\d+$/.test(val)) {
      setAmount(val)
      setOrderError('')
      setOrderSuccess('')
    }
  }

  function handleSelectPreset(val) {
    setAmount(String(val))
    setOrderError('')
    setOrderSuccess('')
  }

  // Step 1: Create Top-Up Order
  async function handleProceedToPayment(e) {
    e.preventDefault()
    if (!isValidAmount || submitting) return

    setSubmitting(true)
    setOrderError('')
    setOrderSuccess('')

    try {
      const res = await api.post('/top-ups', {
        amountInRupees: numAmount,
      })

      const orderData = res.data
      setActiveOrder(orderData)

      // If sandbox mode is enabled, open the sandbox simulation checkout
      if (orderData.sandboxMode) {
        setOrderSuccess(`Top-up order created: ${orderData.orderReference}. Complete verification in the gateway sandbox below.`)
      } else if (orderData.checkoutUrl) {
        // In live gateway mode, redirect or launch provider checkout
        window.location.href = orderData.checkoutUrl
      }
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
        : data?.message || data?.detail || 'Could not initiate top-up order. Please check the amount and try again.'
      setOrderError(msg)
    } finally {
      setSubmitting(false)
    }
  }

  // Step 2: Verify / Complete Payment (Simulated Sandbox or manual verification)
  async function handleSimulateOutcome(outcome) {
    if (!activeOrder || verifyingOutcome) return

    setVerifyingOutcome(outcome)
    setOrderError('')
    setOrderSuccess('')

    try {
      const res = await api.post(`/top-ups/${activeOrder.id}/verify`, {
        outcome,
        providerPaymentId: outcome === 'SUCCESS' ? `pay_sbx_${Date.now().toString(36)}` : null,
      })

      const tx = res.data
      if (tx.status === 'SUCCESS') {
        setOrderSuccess(`✓ Payment verified successfully! ₹${Number(tx.amountInRupees).toLocaleString('en-IN')} has been credited to your confirmed wallet balance.`)
        setActiveOrder(null)
      } else if (tx.status === 'FAILED') {
        setOrderError(`✕ Payment failed: ${tx.failureReason || 'Transaction was declined by the simulated gateway'}. Wallet was not credited.`)
        setActiveOrder(null)
      } else if (tx.status === 'CANCELLED') {
        setOrderError(`✕ Payment cancelled: ${tx.failureReason || 'Cancelled by user'}. Wallet was not credited.`)
        setActiveOrder(null)
      }

      // Refresh wallet balance and transactions from the backend
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
        : data?.message || data?.detail || 'Could not verify payment status.'
      setOrderError(msg)
    } finally {
      setVerifyingOutcome('')
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

  // Stats
  const confirmedBalance = wallet?.balanceInRupees ?? null
  const successfulTxCount = transactions.filter(t => t.status === 'SUCCESS').length
  const totalToppedUp = transactions
    .filter(t => t.status === 'SUCCESS')
    .reduce((acc, t) => acc + (Number(t.amountInRupees) || 0), 0)
  const pendingTxCount = transactions.filter(t => t.status === 'PENDING').length

  return (
    <main className="topup-page">
      {/* ── header ── */}
      <header className="topup-header">
        <button
          id="btn-back-dashboard"
          className="topup-back"
          onClick={() => navigate('/')}
          aria-label="Return to dashboard"
        >
          ← Dashboard
        </button>

        <Link to="/" className="topup-brand" aria-label="EarnTrap dashboard">
          <span className="topup-brand-mark" aria-hidden="true">E</span>
          earntrap
        </Link>

        <div className="topup-header-meta">
          <span className="topup-badge">TOP UP</span>
        </div>
      </header>

      {/* ── main content ── */}
      <section className="topup-content">
        <div className="topup-intro">
          <span className="eyebrow">WALLET DEPOSIT</span>
          <h1>Top Up Wallet</h1>
          <p>
            Add confirmed fiat funds to your platform wallet via encrypted payment checkout.
            Wallet funds are managed independently from your earned journey progression points.
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
            Loading wallet & transactions…
          </div>
        ) : (
          <>
            {/* ── A. Wallet Overview Stats ── */}
            <section className="topup-stats-grid" aria-label="Wallet summary">
              <article className="topup-stat-card featured">
                <div className="topup-stat-top">
                  <span>Confirmed Wallet Balance</span>
                  <span className="topup-stat-icon">₹</span>
                </div>
                <strong className="topup-stat-value">
                  {confirmedBalance !== null ? `₹${Number(confirmedBalance).toLocaleString('en-IN')}` : '—'}
                </strong>
                <small>
                  {confirmedBalance !== null ? 'Verified available funds (INR)' : 'Balance unavailable'}
                </small>
              </article>

              <article className="topup-stat-card">
                <div className="topup-stat-top">
                  <span>Completed Deposits</span>
                  <span className="topup-stat-icon">✓</span>
                </div>
                <strong className="topup-stat-value">
                  {successfulTxCount}
                </strong>
                <small>Total credited: ₹{totalToppedUp.toLocaleString('en-IN')}</small>
              </article>

              <article className="topup-stat-card">
                <div className="topup-stat-top">
                  <span>Pending Orders</span>
                  <span className="topup-stat-icon">◷</span>
                </div>
                <strong className="topup-stat-value">
                  {pendingTxCount}
                </strong>
                <small>{pendingTxCount > 0 ? 'Awaiting gateway verification' : 'No pending orders'}</small>
              </article>

              <article className="topup-stat-card">
                <div className="topup-stat-top">
                  <span>Accounting Mode</span>
                  <span className="topup-stat-icon">🛡</span>
                </div>
                <strong className="topup-stat-value" style={{ fontSize: '20px' }}>
                  Direct Fiat
                </strong>
                <small>Separate from journey points</small>
              </article>
            </section>

            {/* ── B. Top Up Form & Guidelines Layout ── */}
            <div className="topup-form-layout">
              {/* Form Section */}
              <section className="topup-card" aria-label="Top up form">
                <span className="eyebrow">CHOOSE AMOUNT</span>
                <h2>Deposit Funds</h2>

                {orderSuccess && (
                  <div className="alert success-alert" role="status">
                    {orderSuccess}
                  </div>
                )}

                {orderError && (
                  <div className="alert error-alert" role="alert">
                    {orderError}
                  </div>
                )}

                <form onSubmit={handleProceedToPayment} className="topup-form" noValidate>
                  {/* Preset Amount Chips */}
                  <div className="form-group">
                    <label>Select Preset Amount</label>
                    <div className="amount-chips">
                      {PRESET_AMOUNTS.map(preset => (
                        <button
                          key={preset}
                          type="button"
                          className={`amount-chip ${numAmount === preset ? 'active' : ''}`}
                          onClick={() => handleSelectPreset(preset)}
                        >
                          ₹{preset.toLocaleString('en-IN')}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Custom Amount Input */}
                  <div className="form-group">
                    <label htmlFor="topup-amount">Custom Amount in Rupees (₹)</label>
                    <div className="input-with-symbol">
                      <span className="input-symbol" aria-hidden="true">₹</span>
                      <input
                        id="topup-amount"
                        type="text"
                        inputMode="numeric"
                        pattern="[0-9]*"
                        value={amount}
                        disabled={submitting}
                        onChange={handleAmountChange}
                        placeholder="500"
                        aria-describedby="topup-helper-note"
                        required
                      />
                    </div>
                  </div>

                  {/* Payment Summary Breakdown */}
                  <div className="calc-breakdown" aria-label="Payment summary">
                    <div className="calc-row">
                      <span>Deposit Amount:</span>
                      <strong>₹{numAmount > 0 ? numAmount.toLocaleString('en-IN') : '0'}</strong>
                    </div>
                    <div className="calc-row">
                      <span>Gateway & Convenience Fee:</span>
                      <strong style={{ color: '#137a47' }}>₹0 (Free)</strong>
                    </div>
                    <div className="calc-row">
                      <span>Net Wallet Credit:</span>
                      <strong>₹{numAmount > 0 ? numAmount.toLocaleString('en-IN') : '0'}</strong>
                    </div>
                    <div className={`calc-row highlight ${!isValidAmount ? 'disabled-payout' : ''}`}>
                      <span>Total Amount to Pay:</span>
                      <strong>₹{isValidAmount ? numAmount.toLocaleString('en-IN') : '0'}</strong>
                    </div>
                  </div>

                  {/* Contextual Validation Feedback */}
                  <div id="topup-helper-note">
                    {!rawAmount || numAmount === 0 ? (
                      <div className="topup-note info">
                        Enter a deposit amount of ₹100 or more (in whole rupees).
                      </div>
                    ) : !isPositiveInteger ? (
                      <div className="topup-note warning">
                        Please enter whole rupees only (no decimals or special characters).
                      </div>
                    ) : numAmount < 100 ? (
                      <div className="topup-note warning">
                        Minimum top-up amount is ₹100.
                      </div>
                    ) : numAmount > 100000 ? (
                      <div className="topup-note warning">
                        Maximum single top-up amount is ₹1,00,000.
                      </div>
                    ) : (
                      <div className="topup-note info">
                        ✓ Ready: ₹{numAmount.toLocaleString('en-IN')} will be credited atomically to your wallet upon confirmed payment.
                      </div>
                    )}
                  </div>

                  {/* Proceed to Payment Button */}
                  <button
                    type="submit"
                    id="btn-proceed-payment"
                    className="primary-button"
                    disabled={!isValidAmount || submitting}
                  >
                    {submitting ? 'Creating payment order…' : `Proceed to Payment (₹${(isValidAmount ? numAmount : 0).toLocaleString('en-IN')})`}
                    {!submitting && <span>→</span>}
                  </button>
                </form>

                {/* ── Sandbox Simulation Checkout Drawer ── */}
                {activeOrder && activeOrder.sandboxMode && (
                  <div className="sandbox-modal" role="dialog" aria-labelledby="sandbox-title">
                    <div className="sandbox-modal-header">
                      <div>
                        <span className="sandbox-badge">TEST GATEWAY SANDBOX</span>
                        <h3 id="sandbox-title">Payment Verification Simulation</h3>
                      </div>
                      <button
                        type="button"
                        className="sandbox-close"
                        onClick={() => setActiveOrder(null)}
                        aria-label="Close test modal"
                      >
                        ✕
                      </button>
                    </div>

                    <p className="sandbox-desc">
                      This order is running in secure Sandbox test mode. Choose a test outcome to simulate realistic
                      gateway callbacks and verify that wallet balances update atomically and idempotently.
                    </p>

                    <div className="sandbox-details">
                      <div className="sandbox-row">
                        <span>Order Reference:</span>
                        <code>{activeOrder.orderReference}</code>
                      </div>
                      <div className="sandbox-row">
                        <span>Provider Order ID:</span>
                        <code>{activeOrder.providerOrderId}</code>
                      </div>
                      <div className="sandbox-row">
                        <span>Amount to Authorize:</span>
                        <strong>₹{Number(activeOrder.amountInRupees).toLocaleString('en-IN')}</strong>
                      </div>
                    </div>

                    <div className="sandbox-actions">
                      <button
                        type="button"
                        className="sbx-btn sbx-success"
                        disabled={verifyingOutcome !== ''}
                        onClick={() => handleSimulateOutcome('SUCCESS')}
                      >
                        {verifyingOutcome === 'SUCCESS' ? 'Authorizing…' : '✓ Simulate Success (Authorize & Credit)'}
                      </button>
                      <button
                        type="button"
                        className="sbx-btn sbx-fail"
                        disabled={verifyingOutcome !== ''}
                        onClick={() => handleSimulateOutcome('FAILED')}
                      >
                        {verifyingOutcome === 'FAILED' ? 'Processing…' : '✕ Simulate Gateway Failure (Decline)'}
                      </button>
                      <button
                        type="button"
                        className="sbx-btn sbx-cancel"
                        disabled={verifyingOutcome !== ''}
                        onClick={() => handleSimulateOutcome('CANCELLED')}
                      >
                        {verifyingOutcome === 'CANCELLED' ? 'Cancelling…' : '⊘ Cancel Payment (Abandon)'}
                      </button>
                    </div>
                  </div>
                )}
              </section>

              {/* Guidelines Section */}
              <aside className="topup-guidelines-card" aria-label="Deposit guidelines">
                <h3>Deposit & Security Guidelines</h3>

                <div className="topup-guideline-item">
                  <div className="topup-guideline-dot">1</div>
                  <div>
                    <strong>Bank-Grade Encryption</strong>
                    <p>All checkout sessions use 256-bit TLS encryption. Payment credentials are never stored on EarnTrap servers.</p>
                  </div>
                </div>

                <div className="topup-guideline-item">
                  <div className="topup-guideline-dot">2</div>
                  <div>
                    <strong>Instant Atomic Credit</strong>
                    <p>Funds are credited atomically to your wallet balance only after server-side cryptographic payment verification.</p>
                  </div>
                </div>

                <div className="topup-guideline-item">
                  <div className="topup-guideline-dot">3</div>
                  <div>
                    <strong>Separate Accounting</strong>
                    <p>Wallet deposits are maintained as distinct cash balances, strictly separate from earned progression points.</p>
                  </div>
                </div>

                <div className="topup-guideline-item">
                  <div className="topup-guideline-dot">4</div>
                  <div>
                    <strong>Zero Platform Fees</strong>
                    <p>EarnTrap charges ₹0 platform or convenience fees on wallet top-ups. What you deposit is exactly what you get.</p>
                  </div>
                </div>
              </aside>
            </div>

            {/* ── C. Recent Top-Up Transaction History ── */}
            <section className="history-section" aria-label="Top-up history">
              <div className="section-heading">
                <div>
                  <span className="eyebrow">PAYMENT AUDIT LOG</span>
                  <h2>Recent Top-Up Transactions</h2>
                </div>
              </div>

              <div className="topup-history-card">
                {transactions.length === 0 ? (
                  <div className="empty-history">
                    <div className="empty-history-icon" aria-hidden="true">💳</div>
                    <h4>No top-up transactions yet</h4>
                    <p>
                      When you make your first wallet deposit, your reference codes, gateway status, and timestamp records will appear here.
                    </p>
                  </div>
                ) : (
                  <table className="history-table">
                    <thead>
                      <tr>
                        <th>Reference</th>
                        <th>Amount</th>
                        <th>Provider Ref</th>
                        <th>Date</th>
                        <th>Status</th>
                        <th>Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {transactions.map(item => (
                        <tr key={item.id || item.orderReference}>
                          <td>
                            <span className="history-ref">{item.orderReference}</span>
                          </td>
                          <td>
                            <strong className="history-amount">
                              ₹{Number(item.amountInRupees || 0).toLocaleString('en-IN')}
                            </strong>
                          </td>
                          <td>
                            {item.providerPaymentId ? (
                              <code>{item.providerPaymentId}</code>
                            ) : item.providerOrderId ? (
                              <code>{item.providerOrderId}</code>
                            ) : (
                              <span style={{ color: '#9aa8a0', fontSize: '11px' }}>—</span>
                            )}
                          </td>
                          <td>{formatDate(item.createdAt)}</td>
                          <td>
                            <span
                              className={`status-pill status-${(item.status || 'pending').toLowerCase()}`}
                            >
                              {item.status === 'PENDING' && '◷ '}
                              {item.status === 'SUCCESS' && '✓ '}
                              {item.status === 'FAILED' && '✕ '}
                              {item.status === 'CANCELLED' && '⊘ '}
                              {item.status}
                            </span>
                          </td>
                          <td>
                            {item.status === 'PENDING' ? (
                              <button
                                type="button"
                                className="resume-order-btn"
                                onClick={() => setActiveOrder({
                                  id: item.id,
                                  orderReference: item.orderReference,
                                  amountInRupees: item.amountInRupees,
                                  providerOrderId: item.providerOrderId,
                                  sandboxMode: true,
                                })}
                              >
                                Resume Checkout
                              </button>
                            ) : (
                              <span style={{ color: '#88988e', fontSize: '11px' }}>
                                {item.status === 'SUCCESS' ? 'Credited' : 'Closed'}
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
