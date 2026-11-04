import { BarChart3, FileCheck2, Home, LayoutDashboard, Library, MessageSquareText, ShieldCheck, Star, Ticket, Users, Wallet, Store } from 'lucide-react';
import { useT } from '@/lib/i18n';
import { useAuth, hasRole } from '@/lib/auth';
import { AppShell } from './AppShell';
import type { NavItem } from './SideNav';

import '@/features/seller/i18n';

export default function SellerShell() {
  const t = useT();
  const user = useAuth((s) => s.user);
  const nav: NavItem[] = [
    { to: '/teach', labelKey: 'seller.nav.dashboard', icon: LayoutDashboard, end: true },
    { to: '/teach/courses', labelKey: 'seller.nav.courses', icon: Library },
    { to: '/teach/students', labelKey: 'seller.nav.students', icon: Users },
    { to: '/teach/qa', labelKey: 'seller.nav.qa', icon: MessageSquareText },
    { to: '/teach/reviews', labelKey: 'seller.nav.reviews', icon: Star },
    { to: '/teach/analytics', labelKey: 'seller.nav.analytics', icon: BarChart3 },
    { to: '/teach/coupons', labelKey: 'seller.nav.coupons', icon: Ticket },
    { to: '/teach/earnings', labelKey: 'seller.nav.earnings', icon: Wallet },
    { to: '/teach/verification', labelKey: 'seller.nav.verification', icon: FileCheck2 },
    { to: '/teach/storefront', labelKey: 'seller.nav.storefront', icon: Store },
  ];
  const switches = [{ to: '/home', label: t('nav.studentView'), icon: Home }, ...(hasRole(user, 'admin') ? [{ to: '/admin', label: t('nav.admin'), icon: ShieldCheck }] : [])];
  return <AppShell nav={nav} navLabel="teacher" switches={switches} badgeLabel={t('nav.teacherStudio')} />;
}
