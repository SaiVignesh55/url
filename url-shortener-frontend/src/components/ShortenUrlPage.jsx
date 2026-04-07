import React, { useEffect } from 'react'
import { useParams } from 'react-router-dom'

const ShortenUrlPage = () => {
    const { url } = useParams();
    const backendBaseUrl = (import.meta.env.VITE_BACKEND_URL || "").trim();

    useEffect(() => {
        if (url) {
            window.location.href = backendBaseUrl
              ? `${backendBaseUrl}/${url}`
              : `/${url}`;
        }
    }, [backendBaseUrl, url]);
  return <p>Redirecting...</p>;
}

export default ShortenUrlPage