import type { LessonType } from '../../lib/curriculum';
import { FileText, ListTodo, PlayCircle, Timer, Type } from '../../parts';

export const LESSON_ICON: Record<LessonType, typeof PlayCircle> = { video: PlayCircle, pdf: FileText, text: Type, quiz: ListTodo, mock: Timer };
