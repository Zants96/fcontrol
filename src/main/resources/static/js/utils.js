/**
 * MyTwoCents - Utilitários Frontend Globais
 * Concentra funções reutilizáveis de formatação de moeda, datas e sanitização numérica.
 */

window.AppUtils = {
    /**
     * Formata um número ou string numérica para moeda brasileira (BRL).
     * @param {number|string} value - Valor a ser formatado
     * @returns {string} Valor formatado ex: "R$ 1.250,00"
     */
    formatCurrency: function (value) {
        var num = parseFloat(value);
        if (isNaN(num)) return "R$ 0,00";
        return new Intl.NumberFormat("pt-BR", {
            style: "currency",
            currency: "BRL"
        }).format(num);
    },

    /**
     * Formata uma porcentagem com número fixo de casas decimais.
     * @param {number|string} value - Valor percentual
     * @param {number} [decimals=2] - Casas decimais
     * @returns {string} Ex: "12.50%"
     */
    formatPercent: function (value, decimals) {
        if (decimals === undefined) decimals = 2;
        var num = parseFloat(value);
        if (isNaN(num)) return "0.00%";
        return num.toFixed(decimals) + "%";
    },

    /**
     * Converte seguro para float evitando exceções ou NaN.
     * @param {any} val 
     * @param {number} [defaultVal=0] 
     * @returns {number}
     */
    safeParseFloat: function (val, defaultVal) {
        if (defaultVal === undefined) defaultVal = 0;
        if (val === null || val === undefined || val === '') return defaultVal;
        var parsed = parseFloat(val);
        return isNaN(parsed) ? defaultVal : parsed;
    },

    /**
     * Formata uma string no formato "YYYY-MM-DD" para "DD/MM/YYYY".
     * @param {string} dateStr 
     * @returns {string}
     */
    formatDateBR: function (dateStr) {
        if (!dateStr) return "";
        var parts = dateStr.split("-");
        if (parts.length === 3) {
            return parts[2] + "/" + parts[1] + "/" + parts[0];
        }
        return dateStr;
    }
};
