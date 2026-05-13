import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { ToastContainer } from './console/components/ui/Toast'
import 'maplibre-gl/dist/maplibre-gl.css'
import './console/index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
      <ToastContainer />
    </BrowserRouter>
  </React.StrictMode>,
)
