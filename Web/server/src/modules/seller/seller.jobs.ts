import { eq } from 'drizzle-orm';
import { contentDb } from '../../database/client';
import { videoAssets } from '../../database/content/schema';
import { defineJob, enqueue } from '../../common/jobs';
import { videoProvider } from '../../services/video';

/** Poll the provider after upload until the asset is ready (webhooks also land here). */
defineJob<{ videoAssetId: string; attempt?: number }>('video.processed', async ({ videoAssetId, attempt = 0 }) => {
  const [v] = await contentDb.select().from(videoAssets).where(eq(videoAssets.id, videoAssetId));
  if (!v || !v.providerVideoId || v.status === 'ready') return;
  const st = await (await videoProvider()).status(v.providerVideoId);
  await contentDb.update(videoAssets).set({ status: st.status, durationSec: st.durationSec || v.durationSec }).where(eq(videoAssets.id, videoAssetId));
  if (st.status === 'processing' && attempt < 40) await enqueue('video.processed', { videoAssetId, attempt: attempt + 1 }, { delay: 60_000 });
});
