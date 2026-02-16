'use client';

import { ReactNode } from 'react';
import { AuthProvider } from '@/lib/auth-context';
import { QueryProvider } from '@/lib/query-provider';
import { Layout } from '@/components/layout';

export function Providers({ children }: { children: ReactNode }) {
  return (
    <QueryProvider>
      <AuthProvider>
        <Layout>{children}</Layout>
      </AuthProvider>
    </QueryProvider>
  );
}
