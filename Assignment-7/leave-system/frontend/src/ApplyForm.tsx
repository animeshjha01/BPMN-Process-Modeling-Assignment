import { useState } from 'react'
import { submitLeave } from './api'

interface Props {
  name: string
  email: string
  onSubmitted: () => void
}

export default function ApplyForm({ name, email, onSubmitted }: Props) {
  const [leaveType, setLeaveType] = useState('ANNUAL')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [reason, setReason] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim() || !email.trim()) {
      setMessage('Enter your name and email above first.')
      return
    }
    setBusy(true)
    setMessage(null)
    try {
      await submitLeave({
        employeeName: name,
        employeeEmail: email,
        leaveType,
        startDate,
        endDate,
        reason,
      })
      onSubmitted()
    } catch (error: any) {
      setMessage(`Could not submit: ${error.message}`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="card">
      <h2>Apply for leave</h2>
      <label>
        Leave type
        <select value={leaveType} onChange={(e) => setLeaveType(e.target.value)}>
          <option value="ANNUAL">Annual</option>
          <option value="SICK">Sick</option>
          <option value="UNPAID">Unpaid</option>
        </select>
      </label>
      <label>
        From
        <input
          type="date"
          required
          value={startDate}
          onChange={(e) => setStartDate(e.target.value)}
        />
      </label>
      <label>
        To
        <input
          type="date"
          required
          min={startDate}
          value={endDate}
          onChange={(e) => setEndDate(e.target.value)}
        />
      </label>
      <label>
        Reason
        <textarea value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <button disabled={busy}>{busy ? 'Submitting...' : 'Submit request'}</button>
      {message && <p className="error">{message}</p>}
    </form>
  )
}