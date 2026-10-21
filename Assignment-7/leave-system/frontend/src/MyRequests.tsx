import { useEffect, useState } from 'react'
import { balance, myLeaves, type Leave } from './api'

export default function MyRequests({
  email,
  version,
}: {
  email: string
  version: number
}) {
  const [rows, setRows] = useState<Leave[]>([])
  const [remaining, setRemaining] = useState<number | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!email) return
    let alive = true
    const load = async () => {
      try {
        const [requests, leaveBalance] = await Promise.all([
          myLeaves(email),
          balance(email),
        ])
        if (alive) {
          setRows(requests)
          setRemaining(leaveBalance.remainingDays)
          setError('')
        }
      } catch (e: any) {
        if (alive) setError(e.message)
      }
    }
    load()
    const timer = setInterval(load, 3000)
    return () => {
      alive = false
      clearInterval(timer)
    }
  }, [email, version])

  if (!email) return <p>Enter your email above to see your requests.</p>

  return (
    <div className="card">
      <h2>My requests</h2>
      <p>
        <strong>Annual leave remaining:</strong> {remaining ?? '...'} day(s)
      </p>
      {error && <p className="error">{error}</p>}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>#</th>
              <th>Type</th>
              <th>Dates</th>
              <th>Days</th>
              <th>Status</th>
              <th>Note</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td>{r.id}</td>
                <td>{r.leaveType}</td>
                <td>
                  {r.startDate} to {r.endDate}
                </td>
                <td>{r.days}</td>
                <td>
                  <span className={`badge ${r.status}`}>
                    {r.status.replace('_', ' ')}
                  </span>
                </td>
                <td>{r.statusMessage}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}