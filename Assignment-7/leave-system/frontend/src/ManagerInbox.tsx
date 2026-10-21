import { useCallback, useEffect, useState } from 'react'
import { decide, pendingLeaves, type Leave } from './api'

export default function ManagerInbox() {
  const [rows, setRows] = useState<Leave[]>([])
  const [comments, setComments] = useState<Record<number, string>>({})
  const [hidden, setHidden] = useState<number[]>([])
  const [busy, setBusy] = useState<number | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    pendingLeaves()
      .then(setRows)
      .catch((e: any) => setError(e.message))
  }, [])

  useEffect(() => {
    load()
    const timer = setInterval(load, 3000)
    return () => clearInterval(timer)
  }, [load])

  async function act(id: number, approved: boolean) {
    setBusy(id)
    setError('')
    try {
      await decide(id, approved, comments[id] ?? '')
      setHidden((current) => [...current, id])
      load()
    } catch (e: any) {
      setError(e.message)
    } finally {
      setBusy(null)
    }
  }

  const visible = rows.filter((row) => !hidden.includes(row.id))

  return (
    <div>
      <h2>Manager inbox</h2>
      {error && <p className="error">{error}</p>}
      {visible.length === 0 && <p>No requests are waiting for approval.</p>}
      {visible.map((r) => (
        <div key={r.id} className="card">
          <strong>
            #{r.id} {r.employeeName}
          </strong>
          <div>{r.employeeEmail}</div>
          <div>
            {r.leaveType}, {r.startDate} to {r.endDate}, {r.days} working day(s)
          </div>
          {r.reason && <p className="muted">{r.reason}</p>}
          <label>
            Comment
            <input
              placeholder="Optional manager comment"
              value={comments[r.id] ?? ''}
              onChange={(e) =>
                setComments({ ...comments, [r.id]: e.target.value })
              }
            />
          </label>
          <div className="actions">
            <button disabled={busy === r.id} onClick={() => act(r.id, true)}>
              Approve
            </button>
            <button
              disabled={busy === r.id}
              className="danger"
              onClick={() => act(r.id, false)}
            >
              Reject
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}