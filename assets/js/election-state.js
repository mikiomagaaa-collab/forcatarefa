export const electionStart=Date.parse('2026-10-13T03:00:00Z');
export function celebrating(data){const now=Date.parse(data.server_now);const publication=Date.parse(data.published_at);return Boolean(data.visible&&data.status==='eleita'&&Number.isFinite(publication)&&now>=publication&&now<publication+7*86400000);}
