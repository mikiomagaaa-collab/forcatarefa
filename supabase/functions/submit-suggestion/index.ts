import { serve, prepare, plain, db, reply, PublicError } from '../_shared/security.ts';
serve(async req => {
  const state = await prepare(req, 'suggestion', 3);
  if (state.response) return state.response;
  const { input, hash, origin, userId } = state;
  if (input.website !== '') throw new PublicError(400, 'O envio foi recusado.');
  const name = plain(input.name, 0, 60, 'nome');
  const classroom = plain(input.classroom, 0, 30, 'turma');
  const message = plain(input.message, 10, 2000, 'mensagem');
  const category = plain(input.category, 1, 30, 'categoria');
  if (!['Educação','Infraestrutura','Bem-estar','Lazer','Tecnologia','Cultura','Outro'].includes(category)) throw new PublicError(400, 'Selecione uma categoria válida.');
  await db('suggestions', { name, classroom, message, category, session_hash: hash, user_id: userId });
  return reply(origin, { received: true }, 201);
});
