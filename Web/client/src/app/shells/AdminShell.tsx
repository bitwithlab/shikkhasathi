import { FileCheck2, HeartPulse, Home, Inbox, LayoutDashboard, Library, Mail, Megaphone, Receipt, ScrollText, Settings, Undo2, Users, Wallet, Building2, Flag } from 'lucide-react';
import { useT } from '@/lib/i18n';
import { AppShell } from './AppShell';
import type { NavItem } from './SideNav';

import '@/features/admin/i18n';

export default function AdminShell() {
  const t = useT();
  const nav: NavItem[] = [
    { to: '/admin', labelKey: 'admin.nav.dashboard', icon: LayoutDashboard, end: true },
    { to: '/admin/sellers', labelKey: 'admin.nav.sellers', icon: FileCheck2 },
    { to: '/admin/institutions', labelKey: 'admin.nav.institutions', icon: Building2 },
    { to: '/admin/catalog', labelKey: 'admin.nav.catalog', icon: Library },
    { to: '/admin/courses/review', labelKey: 'admin.nav.review', icon: Inbox },
    { to: '/admin/orders', labelKey: 'admin.nav.orders', icon: Receipt },
    { to: '/admin/refunds', labelKey: 'admin.nav.refunds', icon: Undo2 },
    { to: '/admin/payouts', labelKey: 'admin.nav.payouts', icon: Wallet },
    { to: '/admin/users', labelKey: 'admin.nav.users', icon: Users },
    { to: '/admin/reports', labelKey: 'admin.nav.reports', icon: Flag },
    { to: '/admin/notifications', labelKey: 'admin.nav.broadcasts', icon: Megaphone },
    { to: '/admin/settings', labelKey: 'admin.nav.settings', icon: Settings },
    { to: '/admin/health', labelKey: 'admin.nav.health', icon: HeartPulse },
    { to: '/admin/email-logs', labelKey: 'admin.nav.emailLogs', icon: Mail },
    { to: '/admin/audit', labelKey: 'admin.nav.audit', icon: ScrollText },
  ];
  return <AppShell nav={nav} navLabel="admin" switches={[{ to: '/home', label: t('nav.studentView'), icon: Home }]} badgeLabel={t('admin.title')} />;
}
