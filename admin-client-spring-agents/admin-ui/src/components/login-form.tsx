'use client';

import { useState } from 'react';
import { useAuth } from '@/lib/auth-context';
import { Button, Input, Card, CardContent, CardHeader } from './ui';
import { Shield, Eye, EyeOff } from 'lucide-react';

export function LoginForm() {
  const { setToken } = useAuth();
  const [token, setTokenInput] = useState('');
  const [showToken, setShowToken] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!token.trim()) {
      setError('Please enter your admin token');
      return;
    }

    // Set the token - validation will happen on first API call
    setToken(token.trim());
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-900 px-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <div className="flex items-center justify-center mb-4">
            <div className="p-3 bg-blue-600/20 rounded-full">
              <Shield className="w-8 h-8 text-blue-400" />
            </div>
          </div>
          <h1 className="text-2xl font-bold text-center text-white">Admin Panel</h1>
          <p className="text-center text-gray-400 mt-2">
            Enter your admin token to continue
          </p>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="relative">
              <Input
                id="token"
                type={showToken ? 'text' : 'password'}
                label="Admin Token"
                placeholder="Enter your Bearer token"
                value={token}
                onChange={(e) => setTokenInput(e.target.value)}
                error={error}
                className="pr-10"
              />
              <button
                type="button"
                onClick={() => setShowToken(!showToken)}
                className="absolute right-3 top-8 text-gray-400 hover:text-gray-300"
              >
                {showToken ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>

            <Button type="submit" className="w-full">
              Sign In
            </Button>

            <p className="text-xs text-gray-500 text-center mt-4">
              The admin token is configured via the{' '}
              <code className="bg-gray-800 px-1 py-0.5 rounded">AGENT_ADMIN_API_TOKEN</code>{' '}
              environment variable on the server.
            </p>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
