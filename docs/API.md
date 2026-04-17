# FControl – Referência Rápida da API

## Base URL
```
http://localhost:8080/api
```

---

## Lançamentos

### Listar
```
GET /lancamentos?ano=2026
GET /lancamentos?ano=2026&mes=4
GET /lancamentos?ano=2026&categoria=GASTO
GET /lancamentos?ano=2026&mes=4&categoria=RECEITA
```

### Criar
```
POST /lancamentos
Content-Type: application/json

{
  "descricao": "Conta de luz",
  "categoria": "GASTO",
  "subcategoria": "Luz",
  "valor": 120.50,
  "mes": 4,
  "ano": 2026
}
```

### Atualizar
```
PUT /lancamentos/{id}
Content-Type: application/json

{ ... mesmos campos do POST ... }
```

### Excluir
```
DELETE /lancamentos/{id}
```

---

## Dashboard

```
GET /dashboard?ano=2026
```

---

## Categorias válidas
- `RECEITA`
- `GASTO`
- `ASSINATURA`

## Meses válidos
- `1` (Janeiro) a `12` (Dezembro)
