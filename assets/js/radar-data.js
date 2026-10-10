export function radarRows(proposals, progress) {
  const records = new Map(progress.map(row => [row.proposal_id, row]));
  return proposals.filter(p => !p.editorial_state || p.editorial_state === 'Publicada').map(proposal => {
    const record = records.get(proposal.id);
    const recorded = Number.isInteger(record?.completion_percent) && record.completion_percent >= 0 && record.completion_percent <= 100 && record.completion_basis?.trim().length >= 10;
    const completed = record?.stage === 'Realizada';
    return {
      ...proposal,
      percent: completed ? 100 : recorded ? record.completion_percent : null,
      basis: completed && !recorded ? record.note || 'Conclusão registrada pela gestão do grêmio.' : recorded ? record.completion_basis : '',
      stage: record?.stage || 'Apresentada',
      updatedAt: record?.updated_at || null
    };
  }).sort((a, b) => (b.percent ?? -1) - (a.percent ?? -1) || a.id - b.id);
}

export function radarMeasurement(value, basis, stage, note = '') {
  let percent = value.trim() === '' ? null : Number(value);
  let explanation = basis.trim();
  if (stage === 'Realizada') {
    percent = 100;
    explanation ||= note.trim().length >= 10 ? note.trim() : 'Conclusão registrada pela gestão do grêmio.';
  }
  if (percent !== null && (!Number.isInteger(percent) || percent < 0 || percent > 100)) throw new Error('Informe um percentual inteiro entre 0 e 100.');
  if (percent === 100 && stage !== 'Realizada') throw new Error('Para registrar 100%, selecione a etapa Realizada.');
  if (percent !== null && explanation.length < 10) throw new Error('Explique o que foi realizado para justificar o percentual no Radar FT+.');
  if (explanation.length > 1000 || /[<>]/.test(explanation)) throw new Error('Use somente texto, com até 1.000 caracteres, na explicação do progresso.');
  return {completion_percent: percent, completion_basis: percent === null ? '' : explanation};
}
