import { Bell, BookMarked, BookOpen, CalendarDays, ClipboardList, GraduationCap, Home, ListChecks, Megaphone, Presentation, Search, ShieldCheck, Timer, UserCircle } from 'lucide-react';
import { ordinal, useT } from '@/lib/i18n';
import { useAuth, hasRole } from '@/lib/auth';
import { AppShell } from './AppShell';
import type { NavItem } from './SideNav';
import { useUnreadCount } from './useUnread';


export default function StudentShell() {
  const t = useT();
  const user = useAuth((s) => s.user);
  const unread = useUnreadCount();
  const nav: NavItem[] = [
    { to: '/home', labelKey: 'nav.home', icon: Home, end: true },
    { to: '/classroom', labelKey: 'nav.classroom', icon: Presentation, end: true },
    { to: '/classroom/assignments', labelKey: 'nav.assignments', icon: ClipboardList },
    { to: '/classroom/calendar', labelKey: 'nav.calendar', icon: CalendarDays },
    { to: '/my-courses', labelKey: 'nav.myCourses', icon: BookOpen },
    { to: '/explore', labelKey: 'nav.explore', icon: Search },
    { to: '/exam-mode', labelKey: 'nav.examMode', icon: Timer },
    { to: '/mock-tests', labelKey: 'nav.mockTests', icon: ListChecks },
    { to: '/notes', labelKey: 'nav.notes', icon: BookMarked },
    { to: '/requests', labelKey: 'nav.requests', icon: Megaphone },
    { to: '/notifications', labelKey: 'nav.notifications', icon: Bell, badge: unread },
    { to: '/profile', labelKey: 'nav.profile', icon: UserCircle },
  ];
  const bottom: NavItem[] = [
    { to: '/home', labelKey: 'nav.home', icon: Home, end: true },
    { to: '/classroom', labelKey: 'mobile.classroom', icon: Presentation },
    { to: '/my-courses', labelKey: 'mobile.courses', icon: BookOpen },
    { to: '/exam-mode', labelKey: 'mobile.exam', icon: Timer },
    { to: '/profile', labelKey: 'mobile.profile', icon: UserCircle },
  ];
  const switches = [
    ...(hasRole(user, 'seller') ? [{ to: '/teach', label: t('nav.teacherStudio'), icon: GraduationCap }] : []),
    ...(hasRole(user, 'admin') ? [{ to: '/admin', label: t('nav.admin'), icon: ShieldCheck }] : []),
  ];
  const a = user?.academic;
  const badge = a?.institutionShort ? [a.institutionShort, a.departmentShort, a.semester ? t('common.semesterN', { ord: ordinal(a.semester, t.locale) }) : null].filter(Boolean).join(' · ') : null;
  return <AppShell nav={nav} navLabel="student" switches={switches} bottomBar={bottom} unread={unread} badgeLabel={badge} />;
}
