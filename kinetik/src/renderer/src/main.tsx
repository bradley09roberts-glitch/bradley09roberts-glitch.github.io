import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import { installGlobalErrorLogging, log } from './lib/log'
import './styles.css'

installGlobalErrorLogging()
log.info('renderer started')

const root = document.getElementById('root')
if (!root) throw new Error('missing #root element')

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
