import { useState } from 'react'
import ApplyForm from './ApplyForm'
import MyRequests from './MyRequests'
import ManagerInbox from './ManagerInbox'
import './App.css'

type Tab = 'apply' | 'mine' | 'manager'

export default function App() {
  const [tab, setTab] = useState<Tab>('apply')
  const [name, setName] = useState(localStorage.getItem('name') ?? '')
  const [email, setEmail] = useState(localStorage.getItem('email') ?? '')
  const [version, setVersion] = useState(0)

  const save = (key: string, value: string, setter: (v: string) => void) => {
    localStorage.setItem(key, value)
    setter(value)
  }

  return (
    <div className="app">
      <h1>Leave portal</h1>
      <div className="who">
        <input
          placeholder="Your name"
          value={name}
          onChange={(e) => save('name', e.target.value, setName)}
        />
        <input
          placeholder="Your email"
          value={email}
          onChange={(e) => save('email', e.target.value, setEmail)}
        />
      </div>
      <nav>
        <button
          className={tab === 'apply' ? 'on' : ''}
          onClick={() => setTab('apply')}
        >
          Apply
        </button>
        <button
          className={tab === 'mine' ? 'on' : ''}
          onClick={() => setTab('mine')}
        >
          My requests
        </button>
        <button
          className={tab === 'manager' ? 'on' : ''}
          onClick={() => setTab('manager')}
        >
          Manager inbox
        </button>
      </nav>
      {tab === 'apply' && (
        <ApplyForm
          name={name}
          email={email}
          onSubmitted={() => {
            setVersion((v) => v + 1)
            setTab('mine')
          }}
        />
      )}
      {tab === 'mine' && <MyRequests email={email} version={version} />}
      {tab === 'manager' && <ManagerInbox />}
    </div>
  )
}