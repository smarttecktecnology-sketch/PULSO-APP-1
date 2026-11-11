# SMART TECK ORBIT

**Sua vida organizada. Seu tempo sob controle.**

Aplicativo Android de organização pessoal, desenvolvido com Kotlin e Jetpack Compose.

## Funcionalidades implementadas nesta versão

- Painel inicial com resumo de tarefas e arquivos.
- Navegação entre Início, Tarefas, Agenda e Arquivos.
- Cadastro de tarefas com data opcional.
- Marcar tarefas como concluídas e excluir tarefas.
- Persistência local de tarefas e referências de documentos.
- Seleção de arquivos pelo seletor nativo do Android.
- Interface em tema azul-escuro com cartões, indicadores e atalhos.

## Em desenvolvimento / ainda não conectado

- Assistente de IA para interpretar documentos e mensagens.
- Extração automática e validação de datas.
- Notificações e lembretes.
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
