// Teacher Studio shared code. Every seller route imports from here, so Rollup emits one shared
// seller chunk instead of many small ones (fewer entries in the main bundle's preload map).
import './i18n';

export * from './icons';
export * from './ApplicationForm';
export * from './ChipsInput';
export * from './Combobox';
export * from './FileDrop';
export * from './InstitutionPicker';
export * from './ListEditor';
export * from './StatusBadge';
export * from './VerificationBanner';
export * from '../lib/money';
export * from '../lib/slug';
export * from '../lib/stats';
export * from '../lib/upload';
