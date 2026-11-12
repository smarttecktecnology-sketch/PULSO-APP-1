# SMART ORBIT

**Sua vida organizada. Seu tempo sob controle.**

Aplicativo Android de organização pessoal, desenvolvido com Kotlin e Jetpack Compose.

## Funcionalidades implementadas nesta versão

- Painel inicial com resumo de tarefas e arquivos.
- Navegação entre Início, Tarefas, Agenda e Arquivos.
- Cadastro e edição de tarefas com seleção visual de data e horário do lembrete.
- Marcar tarefas como concluídas e excluir tarefas.
- Agendamento de lembretes locais com WorkManager e recuperação dos lembretes pendentes ao abrir o app; os horários podem sofrer pequenos atrasos do Android.
- Priorização das próximas tarefas e identificação visual de tarefas atrasadas.
- Persistência local de tarefas e referências de documentos.
- Seleção de arquivos pelo seletor nativo do Android e abertura pelo aplicativo compatível instalado.
- Interface em tema azul-escuro com cartões, indicadores e atalhos.

## Em desenvolvimento / ainda não conectado

- Assistente de IA para interpretar documentos e mensagens.
- Extração automática e validação de datas.
- Melhorias de interface para seleção de data/hora e edição de tarefas.
- Sincronização entre dispositivos e backend.
- Login e recuperação de conta.
- Assinaturas Google Play Billing, validação de compra no servidor e controle de planos.
- Criptografia e política completa de retenção/backup.

O assistente exibido no aplicativo é, por enquanto, uma tela informativa; não envia conteúdo para um modelo de IA. Os documentos selecionados permanecem no local original do dispositivo: o app guarda uma referência URI, não envia nem copia os arquivos para a nuvem.

## Requisitos

- Android Studio
- JDK 17
- Android SDK 35
- Gradle 8.9 ou compatível com Android Gradle Plugin 8.7.3

Abra o repositório no Android Studio e execute a configuração/compilação do módulo `:app`.

## Privacidade e segurança

Ações importantes devem exigir aprovação explícita. O usuário deve poder revisar, corrigir, exportar e excluir seus dados. Antes de lançar, implementar armazenamento protegido, estratégia de backup, exclusão completa e controles de acesso adequados.

## Planos planejados

Free, ORBIT Start, ORBIT Plus e ORBIT Ultra. Os preços e limites permanecem provisórios até a estimativa de custos de infraestrutura. A expiração de uma assinatura não deve bloquear o acesso razoável aos dados do próprio usuário.

## Identificação técnica

- Application ID: `com.smartteck.orbit`
- Versão: `0.1.0`
- Min SDK: 24
- Target SDK: 35

> O repositório contém código-fonte. Um APK ainda precisa ser compilado, testado em dispositivos reais, assinado e preparado para distribuição.


## SMART ORBIT Premium

- O app possui uma tela inicial de apresentação do plano Premium e a camada de integração com Google Play Billing Library 8.0.0.
- ID de produto de assinatura reservado: `orbit_premium_monthly` (deve ser criado no Google Play Console com plano-base mensal correspondente).
- O botão de compra permanece intencionalmente desativado até a IA Premium estar implementada e testada, evitando cobrar por uma função indisponível.
- O aplicativo não utiliza servidor próprio. A consulta de compras é feita pelo Google Play Billing no dispositivo; para validação de compra mais resistente a adulterações, a documentação do Google recomenda verificação no servidor. Essa limitação precisa ser aceita conscientemente antes da publicação comercial.
- A IA avançada ainda não está incluída nesta etapa; o assistente local atual continua gratuito.
