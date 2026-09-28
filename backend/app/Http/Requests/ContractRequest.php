<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Validator;

abstract class ContractRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    public function validationData(): array
    {
        return $this->isMethod('GET') ? $this->query() : $this->json()->all();
    }

    public function withValidator(Validator $validator): void
    {
        $validator->after(function (Validator $validator) {
            foreach (array_diff(array_keys($this->validationData()), array_keys($this->rules())) as $field) {
                $validator->errors()->add($field, 'Campo no admitido.');
            }
            if (! $this->isMethod('GET') && $this->query()) {
                $validator->errors()->add('query', 'No se admiten parámetros de consulta en escrituras.');
            }
        });
    }
}
