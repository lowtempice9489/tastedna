// 화면과 테스트가 공유하는 선택 규칙. 최종 검증은 서버에서 다시 수행한다.
export function activeGroups(question, selections = {}) {
  const active = new Set();
  return (question.comparisons || []).filter(group => {
    const c = group.showWhen;
    const enabled = !c || (active.has(c.comparisonId) && c.optionIds.includes(selections[c.comparisonId]));
    if (enabled) active.add(group.id);
    return enabled;
  });
}
export function selectionMap(answer) {
  return Object.fromEntries((answer?.selections || []).map(s => [s.comparisonId, s.selectedOptionId]));
}
export function choose(question, answer, optionId, groupId) {
  if (question.kind === 'COMPARISON') {
    const selected = {...selectionMap(answer), [groupId]: optionId};
    const groups = activeGroups(question, selected);
    return {selections: groups.filter(g => selected[g.id]).map(g => ({comparisonId:g.id, selectedOptionId:selected[g.id]}))};
  }
  if (question.kind === 'SINGLE_CHOICE') return {selectedOptionId: optionId};
  const exclusive = question.exclusiveOptionIds || [];
  let ids = [...(answer?.selectedOptionIds || [])];
  if (ids.includes(optionId)) ids = ids.filter(id => id !== optionId);
  else if (exclusive.includes(optionId)) ids = [optionId];
  else {
    ids = ids.filter(id => !exclusive.includes(id));
    if (ids.length >= question.maxSelections) return answer;
    ids.push(optionId);
  }
  return {selectedOptionIds:ids};
}
export function valid(question, answer) {
  if (!answer) return false;
  if (question.kind === 'COMPARISON') {
    const selections = selectionMap(answer), groups = activeGroups(question, selections);
    return answer.selections?.length === groups.length
      && Object.keys(selections).length === groups.length
      && groups.every(g => g.options.some(o => o.id === selections[g.id]));
  }
  const ids = question.kind === 'SINGLE_CHOICE' ? [answer.selectedOptionId] : answer.selectedOptionIds;
  return Array.isArray(ids) && ids.length > 0 && new Set(ids).size === ids.length
    && ids.length <= (question.maxSelections || 1)
    && ids.every(id => question.options.some(o => o.id === id))
    && (ids.length === 1 || !ids.some(id => (question.exclusiveOptionIds || []).includes(id)));
}

