import { serve, prepare, db, reply } from '../_shared/security.ts';
serve(async req => {
  const state = await prepare(req, 'intention', 3);
  if (state.response) return state.response;
  const recorded = await db('rpc/record_intention', { target_hash: state.hash });
  return reply(state.origin, { recorded });
});
