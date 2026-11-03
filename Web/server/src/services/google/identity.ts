import { createRemoteJWKSet, jwtVerify } from 'jose';
import { isProd } from '../../config/env';
import { unauthorized } from '../../common/errors';
import { getIntegration } from '../../modules/settings/settings.service';

export interface GoogleIdentity {
  sub: string;
  email: string;
  emailVerified: boolean;
  name: string;
  picture: string | null;
}

const jwks = createRemoteJWKSet(new URL('https://www.googleapis.com/oauth2/v3/certs'));

/**
 * Verifies a Google ID token (web GIS / Android Credential Manager / iOS GoogleSignIn).
 * Without a configured client id (dev/test only) accepts `fake:<email>:<name>` tokens.
 */
export async function verifyGoogleIdToken(idToken: string): Promise<GoogleIdentity> {
  const cfg = await getIntegration('google');
  const audiences = [cfg.client_id, cfg.android_client_id, cfg.ios_client_id].filter((v): v is string => !!v);
  if (audiences.length === 0) {
    if (isProd) throw unauthorized('Google সাইন-ইন এখন চালু নেই।');
    const [p, email, name] = idToken.split(':');
    if (p !== 'fake' || !email) throw unauthorized('Google টোকেন যাচাই করা যায়নি।');
    return { sub: `fake-${email}`, email: email.toLowerCase(), emailVerified: true, name: name || email.split('@')[0]!, picture: null };
  }
  try {
    const { payload } = await jwtVerify(idToken, jwks, { issuer: ['https://accounts.google.com', 'accounts.google.com'], audience: audiences });
    if (!payload.sub || typeof payload.email !== 'string') throw new Error('missing claims');
    return {
      sub: payload.sub,
      email: payload.email.toLowerCase(),
      emailVerified: payload.email_verified === true,
      name: typeof payload.name === 'string' ? payload.name : payload.email.split('@')[0]!,
      picture: typeof payload.picture === 'string' ? payload.picture : null,
    };
  } catch {
    throw unauthorized('Google টোকেন যাচাই করা যায়নি।');
  }
}
