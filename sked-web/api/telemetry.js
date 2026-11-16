export default async function handler(req, res) {
  // CORS configuration for app and web inspection
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-App-Id, User-Agent');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  const kvUrl = process.env.KV_REST_API_URL || process.env.UPSTASH_REDIS_REST_URL || process.env.STORAGE_REST_API_URL || process.env.STORAGE_URL;
  const kvToken = process.env.KV_REST_API_TOKEN || process.env.UPSTASH_REDIS_REST_TOKEN || process.env.STORAGE_REST_API_TOKEN;

  async function kvPipeline(commands) {
    if (!kvUrl || !kvToken || !Array.isArray(commands) || commands.length === 0) return [];
    try {
      const resp = await fetch(`${kvUrl}/pipeline`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${kvToken}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(commands)
      });
      if (!resp.ok) return [];
      const data = await resp.json();
      return Array.isArray(data) ? data.map(d => d.result) : [];
    } catch (e) {
      console.error('KV Pipeline Error:', e);
      return [];
    }
  }

  const query = req.query || {};
  const action = query.action;
  const key = query.key;
  const event = query.event;
  const batch = query.batch;
  const dept = query.dept;
  const deviceId = query.deviceId;

  // 1. PING / HIT EVENT (Records telemetry)
  if (action === 'hit' || event === 'ping') {
    const today = new Date().toISOString().slice(0, 10).replace(/-/g, '_');
    const cmds = [];

    if (key) {
      cmds.push(['INCR', `sked:${key}`]);
    }
    if (batch) {
      cmds.push(['INCR', `sked:batch_${batch}`]);
    }
    if (dept) {
      cmds.push(['INCR', `sked:dept_${dept}`]);
    }
    if (deviceId) {
      cmds.push(['SADD', 'sked:unique_devices', deviceId]);
      cmds.push(['SADD', `sked:dau_${today}`, deviceId]);
      cmds.push(['EXPIRE', `sked:dau_${today}`, '604800']); // 7 days retention
    }

    if (cmds.length > 0) {
      await kvPipeline(cmds);
    }

    return res.status(200).json({ status: 'ok' });
  }

  // 2. QUERY TELEMETRY (For Executive System Console)
  const today = new Date().toISOString().slice(0, 10).replace(/-/g, '_');

  const pipelineResults = await kvPipeline([
    ['SCARD', 'sked:unique_devices'],
    ['GET', 'sked:installs'],
    ['SCARD', `sked:dau_${today}`],
    ['GET', `sked:dau_${today}`],
    ['GET', `sked:syncs_${today}`],
    ['GET', 'sked:batch_2026'],
    ['GET', 'sked:batch_2025'],
    ['GET', 'sked:batch_2024'],
    ['GET', 'sked:batch_2023'],
    ['GET', 'sked:batch_other'],
    ['GET', 'sked:dept_cse'],
    ['GET', 'sked:dept_mgmt'],
    ['GET', 'sked:dept_ece'],
    ['GET', 'sked:dept_mech'],
    ['GET', 'sked:dept_bio'],
    ['GET', 'sked:dept_pharma'],
    ['GET', 'sked:dept_law'],
    ['GET', 'sked:dept_design'],
    ['GET', 'sked:dept_other']
  ]);

  const [
    uniqueSetCount,
    installsKeyCount,
    dauSetCount,
    dauKeyCount,
    widgetSyncs,
    b2026, b2025, b2024, b2023, bOther,
    dCse, dMgmt, dEce, dMech, dBio, dPharma, dLaw, dDesign, dOther
  ] = pipelineResults;

  const totalInstalls = Number(uniqueSetCount) || Number(installsKeyCount) || 0;
  const dauToday = Number(dauSetCount) || Number(dauKeyCount) || 0;

  return res.status(200).json({
    totalInstalls,
    dauToday,
    widgetSyncsToday: Number(widgetSyncs) || 0,
    batch2026: Number(b2026) || 0,
    batch2025: Number(b2025) || 0,
    batch2024: Number(b2024) || 0,
    batch2023: Number(b2023) || 0,
    batchOther: Number(bOther) || 0,
    deptCse: Number(dCse) || 0,
    deptMgmt: Number(dMgmt) || 0,
    deptEce: Number(dEce) || 0,
    deptMech: Number(dMech) || 0,
    deptBio: Number(dBio) || 0,
    deptPharma: Number(dPharma) || 0,
    deptLaw: Number(dLaw) || 0,
    deptDesign: Number(dDesign) || 0,
    deptOther: Number(dOther) || 0,
    isLive: Boolean(kvUrl && kvToken)
  });
}
