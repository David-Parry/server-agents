'use client';

import { useState } from 'react';
import { useAuth } from '@/lib/auth-context';
import { api } from '@/lib/api';
import {
  Card,
  CardContent,
  CardHeader,
  Button,
  Input,
  Badge,
} from '@/components/ui';
import { Settings, Key, Server, Shield, Check, Copy, Eye, EyeOff } from 'lucide-react';

export default function SettingsPage() {
  const { token, setToken, logout } = useAuth();
  const [newToken, setNewToken] = useState('');
  const [showToken, setShowToken] = useState(false);
  const [copied, setCopied] = useState(false);
  const [apiUrl, setApiUrl] = useState(
    typeof window !== 'undefined' ? localStorage.getItem('api_url') || '' : ''
  );

  const handleUpdateToken = (e: React.FormEvent) => {
    e.preventDefault();
    if (newToken.trim()) {
      setToken(newToken.trim());
      setNewToken('');
    }
  };

  const handleUpdateApiUrl = (e: React.FormEvent) => {
    e.preventDefault();
    if (typeof window !== 'undefined') {
      localStorage.setItem('api_url', apiUrl);
      window.location.reload();
    }
  };

  const handleCopyToken = () => {
    if (token) {
      navigator.clipboard.writeText(token);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">Settings</h1>
        <p className="text-gray-400 mt-1">Configure your admin panel settings</p>
      </div>

      {/* Current Token */}
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-white flex items-center gap-2">
            <Key className="w-5 h-5 text-yellow-400" />
            Current Admin Token
          </h2>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center gap-2">
            <Badge variant="success">Active</Badge>
            <span className="text-sm text-gray-400">Token is configured and active</span>
          </div>

          <div className="relative">
            <div className="p-3 bg-gray-900 rounded-lg font-mono text-sm text-gray-300 break-all pr-20">
              {showToken ? token : '•'.repeat(Math.min(token?.length || 0, 50))}
            </div>
            <div className="absolute right-2 top-2 flex gap-1">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowToken(!showToken)}
              >
                {showToken ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </Button>
              <Button variant="ghost" size="sm" onClick={handleCopyToken}>
                {copied ? (
                  <Check className="w-4 h-4 text-green-400" />
                ) : (
                  <Copy className="w-4 h-4" />
                )}
              </Button>
            </div>
          </div>

          <Button variant="danger" onClick={logout}>
            Logout / Clear Token
          </Button>
        </CardContent>
      </Card>

      {/* Update Token */}
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-white flex items-center gap-2">
            <Shield className="w-5 h-5 text-blue-400" />
            Update Admin Token
          </h2>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleUpdateToken} className="space-y-4">
            <Input
              label="New Admin Token"
              type="password"
              placeholder="Enter new admin token"
              value={newToken}
              onChange={(e) => setNewToken(e.target.value)}
            />
            <p className="text-xs text-gray-500">
              Update your admin token if it has been rotated on the server.
            </p>
            <Button type="submit" disabled={!newToken.trim()}>
              Update Token
            </Button>
          </form>
        </CardContent>
      </Card>

      {/* API Configuration */}
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-white flex items-center gap-2">
            <Server className="w-5 h-5 text-green-400" />
            API Configuration
          </h2>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleUpdateApiUrl} className="space-y-4">
            <Input
              label="API Base URL"
              type="url"
              placeholder="http://localhost:8080"
              value={apiUrl}
              onChange={(e) => setApiUrl(e.target.value)}
            />
            <p className="text-xs text-gray-500">
              Leave empty to use the same origin as the admin panel. Only change this if your API
              is hosted on a different server.
            </p>
            <Button type="submit">Save & Reload</Button>
          </form>
        </CardContent>
      </Card>

      {/* About */}
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-white flex items-center gap-2">
            <Settings className="w-5 h-5 text-purple-400" />
            About
          </h2>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            <div className="flex justify-between">
              <span className="text-gray-400">Application</span>
              <span className="text-white">Spring Agents Admin Panel</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-400">API Version</span>
              <span className="text-white">1.0.0</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-400">Authentication</span>
              <span className="text-white">Bearer Token</span>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
