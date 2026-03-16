'use client';

import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Card, CardContent, CardHeader, StatCard, Spinner, Badge } from '@/components/ui';
import { Users, Cpu, Shield, FileText, Activity, Key } from 'lucide-react';
import { format } from 'date-fns';

export default function DashboardPage() {
  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['system-stats'],
    queryFn: () => api.getSystemStatistics(),
  });

  const { data: auditStats, isLoading: auditLoading } = useQuery({
    queryKey: ['audit-stats'],
    queryFn: () => api.getAuditStatistics(),
  });

  const { data: tokenStats, isLoading: tokenLoading } = useQuery({
    queryKey: ['token-stats'],
    queryFn: () => api.getTokenStatistics(),
  });

  if (statsLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner className="w-8 h-8" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">Dashboard</h1>
        <p className="text-gray-400 mt-1">Overview of your Spring Agents system</p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Customers"
          value={stats?.totalCustomers ?? 0}
          icon={<Users className="w-8 h-8" />}
        />
        <StatCard
          title="Enabled Customers"
          value={stats?.enabledCustomers ?? 0}
          icon={<Users className="w-8 h-8 text-green-500" />}
        />
        <StatCard
          title="Total Models"
          value={stats?.totalModels ?? 0}
          icon={<Cpu className="w-8 h-8" />}
        />
        <StatCard
          title="Enabled Models"
          value={stats?.enabledModels ?? 0}
          icon={<Cpu className="w-8 h-8 text-green-500" />}
        />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <StatCard
          title="Policy Types"
          value={stats?.totalPolicyTypes ?? 0}
          icon={<Shield className="w-8 h-8" />}
        />
        <StatCard
          title="Total Allowances"
          value={stats?.totalAllowances ?? 0}
          icon={<Activity className="w-8 h-8" />}
        />
        <StatCard
          title="Audit Logs"
          value={stats?.totalAuditLogs ?? 0}
          icon={<FileText className="w-8 h-8" />}
        />
      </div>

      {/* Recent Activity & Token Stats */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Audit Activity */}
        <Card>
          <CardHeader>
            <h2 className="text-lg font-semibold text-white flex items-center gap-2">
              <Activity className="w-5 h-5 text-blue-400" />
              Recent Activity (24h)
            </h2>
          </CardHeader>
          <CardContent>
            {auditLoading ? (
              <div className="flex justify-center py-8">
                <Spinner />
              </div>
            ) : auditStats ? (
              <div className="space-y-3">
                <div className="flex justify-between items-center text-sm">
                  <span className="text-gray-400">Total Events</span>
                  <span className="text-white font-medium">{auditStats.totalEvents}</span>
                </div>
                <div className="border-t border-gray-700 pt-3 space-y-2">
                  {Object.entries(auditStats.eventCountsByType || {})
                    .sort(([, a], [, b]) => b - a)
                    .slice(0, 5)
                    .map(([type, count]) => (
                      <div key={type} className="flex justify-between items-center">
                        <span className="text-sm text-gray-400">{type.replace(/_/g, ' ')}</span>
                        <Badge variant="info">{count}</Badge>
                      </div>
                    ))}
                </div>
                {auditStats.since && (
                  <p className="text-xs text-gray-500 pt-2 border-t border-gray-700">
                    Since {format(new Date(auditStats.since), 'MMM d, yyyy HH:mm')}
                  </p>
                )}
              </div>
            ) : (
              <p className="text-gray-500 text-center py-4">No activity data available</p>
            )}
          </CardContent>
        </Card>

        {/* Token Statistics */}
        <Card>
          <CardHeader>
            <h2 className="text-lg font-semibold text-white flex items-center gap-2">
              <Key className="w-5 h-5 text-yellow-400" />
              Token Statistics
            </h2>
          </CardHeader>
          <CardContent>
            {tokenLoading ? (
              <div className="flex justify-center py-8">
                <Spinner />
              </div>
            ) : tokenStats && tokenStats.length > 0 ? (
              <div className="space-y-3">
                {tokenStats.map((stat) => (
                  <div
                    key={stat.secretVersion}
                    className="flex justify-between items-center p-3 bg-gray-800/50 rounded-lg"
                  >
                    <div>
                      <p className="text-sm font-medium text-white">
                        Version: {stat.secretVersion}
                      </p>
                      <p className="text-xs text-gray-400">Secret Version</p>
                    </div>
                    <div className="text-right">
                      <p className="text-lg font-semibold text-white">
                        {stat.activeTokenCount}
                      </p>
                      <p className="text-xs text-gray-400">Active Tokens</p>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-gray-500 text-center py-4">No token statistics available</p>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Quick Actions */}
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-white">Quick Actions</h2>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <a
              href="/customers"
              className="p-4 bg-gray-800/50 rounded-lg hover:bg-gray-700/50 transition-colors text-center"
            >
              <Users className="w-8 h-8 mx-auto text-blue-400 mb-2" />
              <p className="text-sm font-medium text-white">Manage Customers</p>
            </a>
            <a
              href="/models"
              className="p-4 bg-gray-800/50 rounded-lg hover:bg-gray-700/50 transition-colors text-center"
            >
              <Cpu className="w-8 h-8 mx-auto text-green-400 mb-2" />
              <p className="text-sm font-medium text-white">Manage Models</p>
            </a>
            <a
              href="/policies"
              className="p-4 bg-gray-800/50 rounded-lg hover:bg-gray-700/50 transition-colors text-center"
            >
              <Shield className="w-8 h-8 mx-auto text-yellow-400 mb-2" />
              <p className="text-sm font-medium text-white">Policy Types</p>
            </a>
            <a
              href="/audit"
              className="p-4 bg-gray-800/50 rounded-lg hover:bg-gray-700/50 transition-colors text-center"
            >
              <FileText className="w-8 h-8 mx-auto text-purple-400 mb-2" />
              <p className="text-sm font-medium text-white">View Audit Logs</p>
            </a>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
