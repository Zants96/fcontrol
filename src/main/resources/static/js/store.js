/**
 * MyTwoCents - Centralized Application State Store
 * Padrão Observer (Pub/Sub) para gerenciar o estado da SPA desacoplado do DOM.
 */

window.AppStore = (function () {
    var state = {
        lancamentos: [],
        ativos: [],
        dashboard: null,
        investimentoDashboard: null,
        selectedYear: new Date().getFullYear(),
        selectedMonth: null,
        loading: false
    };

    var listeners = [];

    return {
        /**
         * Retorna o estado atual (cópia de leitura).
         */
        getState: function () {
            return Object.assign({}, state);
        },

        /**
         * Atualiza partes do estado e notifica os inscritos.
         * @param {Object} newState 
         */
        setState: function (newState) {
            state = Object.assign({}, state, newState);
            this.notify();
        },

        /**
         * Inscreve um callback para ser executado a cada mudança de estado.
         * @param {Function} listener 
         * @returns {Function} Função para cancelar a inscrição
         */
        subscribe: function (listener) {
            if (typeof listener === 'function') {
                listeners.push(listener);
            }
            return function () {
                listeners = listeners.filter(function (l) { return l !== listener; });
            };
        },

        /**
         * Dispara a notificação para todos os ouvintes.
         */
        notify: function () {
            var currentState = this.getState();
            listeners.forEach(function (listener) {
                try {
                    listener(currentState);
                } catch (e) {
                    console.error("Erro no listener da AppStore: ", e);
                }
            });
        }
    };
})();
