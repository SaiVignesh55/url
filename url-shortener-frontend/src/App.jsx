import './App.css'
import { BrowserRouter as Router } from 'react-router-dom'
import { getApps } from './utils/helper'

const CurrentApp = getApps();

function App() {

  return (
    <Router>
      <CurrentApp />
    </Router>
  )
}

export default App