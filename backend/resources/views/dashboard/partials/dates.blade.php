<div><label for="from">Desde · Lima</label><input type="date" id="from" name="from" value="{{ $filters['from'] ?? request('from') }}"></div>
<div><label for="to">Hasta · Lima (inclusive)</label><input type="date" id="to" name="to" value="{{ $filters['to'] ?? request('to') }}"></div>
