import type { FastifyInstance } from 'fastify';
import { contracts } from '@shikkhasathi/shared';
import { route } from '../../common/route';
import * as svc from './catalog.service';

export async function catalogRoutes(app: FastifyInstance) {
  const c = contracts.catalog;
  const cache = { cacheControl: 'public, max-age=300, stale-while-revalidate=3600' };
  route(app, c.institutions, ({ query }) => svc.listInstitutions(query), cache);
  route(app, c.institution, ({ params }) => svc.institutionBySlug(params.slug), cache);
  route(app, c.departments, ({ query }) => svc.listDepartments(query.institutionId), cache);
  route(app, c.programs, ({ query }) => svc.listPrograms(query.departmentId), cache);
  route(app, c.subjects, ({ query }) => svc.listSubjects(query), cache);
  route(app, c.subject, ({ params }) => svc.subjectBySlug(params.slug), cache);
  route(app, c.requestInstitution, ({ user, body }) => svc.requestInstitution(user.id, body), { rateLimit: { max: 5, timeWindow: '1 hour' } });
  route(app, c.academicProfile, ({ user, body }) => svc.setAcademicProfile(user.id, body));
  route(app, c.completeOnboarding, ({ user, body }) => svc.completeOnboarding(user.id, body));

  const a = contracts.adminCatalog;
  route(app, a.institutionRequests, ({ query }) => svc.adminListRequests(query));
  route(app, a.decideInstitutionRequest, ({ params, body, user }) => svc.adminDecideRequest(params.id, body, user.id));
  route(app, a.institutions, ({ query }) => svc.adminListInstitutions(query));
  route(app, a.createInstitution, ({ body, user }) => svc.adminUpsertInstitution(user.id, body));
  route(app, a.updateInstitution, ({ params, body, user }) => svc.adminUpsertInstitution(user.id, body, params.id));
  route(app, a.mergeInstitution, ({ params, body, user }) => svc.adminMergeInstitution(user.id, params.id, body.intoId));
  route(app, a.createDepartment, ({ body, user }) => svc.adminUpsertDepartment(user.id, body));
  route(app, a.updateDepartment, ({ params, body, user }) => svc.adminUpsertDepartment(user.id, body, params.id));
  route(app, a.deleteDepartment, ({ params, user }) => svc.adminDeleteDepartment(user.id, params.id));
  route(app, a.createProgram, ({ body, user }) => svc.adminCreateProgram(user.id, body));
  route(app, a.deleteProgram, ({ params, user }) => svc.adminDeleteProgram(user.id, params.id));
  route(app, a.subjects, ({ query }) => svc.adminListSubjects(query));
  route(app, a.createSubject, ({ body, user }) => svc.adminUpsertSubject(user.id, body));
  route(app, a.updateSubject, ({ params, body, user }) => svc.adminUpsertSubject(user.id, body, params.id));
  route(app, a.createInstitutionSubject, ({ body, user }) => svc.adminCreateInstitutionSubject(user.id, body));
  route(app, a.deleteInstitutionSubject, ({ params, user }) => svc.adminDeleteInstitutionSubject(user.id, params.id));
}
