# BIAN Service Landscape v14.0: catálogo de Service Domains

Catálogo extraído em 2026-10-19 da **Matrix View** do portal oficial ([BIAN Service Landscape 14.0.0](https://bian.org/servicelandscape-14-0-0/), visão "BIAN Service Landscape V14.0 Matrix View"). Os nomes estão em inglês, exatamente como o BIAN publica, e devem ser usados assim em artefatos e código.

Use este arquivo para **localizar** o Service Domain (SD) certo. Definição, Control Record, Behavior Qualifiers e Service Operations de cada SD ficam no portal e nas Semantic APIs (veja `bian-architecture-skill.md`, seção 9).

## Como ler

- **Hierarquia:** Business Area → Business Domain → Service Domain.
- **Agrupadores:** em *Operations and Execution*, os Business Domains são reunidos em dois agrupadores, **Product Specific Fulfillment** e **Cross Product Operations**, indicados em itálico ao lado do nome.
- **Contagem:** a Matrix View exibe **5 Business Areas, 38 Business Domains (36 + 2 agrupadores) e 341 Service Domains**. As release notes da v14.0 informam **322 SDs** e **242 Semantic APIs**. A diferença pode vir de SDs desenhados sem definição completa. Antes de usar um SD como contrato, confirme-o no portal.

## Mudanças da v14.0 que afetam o mapeamento

Fonte: [BIAN v14.0 Release Notes v1.0](https://bian.org/wp-content/uploads/2026/02/BIAN-v14.0-Release-Notes-v1.0_-Final-Version.pdf), de 20/02/2026.

| Tipo | Itens |
|---|---|
| SDs novos | Incentive Program Directory, Rewards Menu, Rewards Inventory, Rewards Delivery, Incentive Account, Issued Certificate Directory, Partner Management, Operations Log, Partner Administration, Payee Management, Customer Consent, Payment Orchestration, Payment Confirmation, Payment Settlement, Corporate Card Service, Funeral Policy, Claim Assessment, Claim Administration |
| SDs obsoletos (não usar) | Payment Execution → Payment Settlement; Payment Instruction → Payment Order Initiation; Payment Order → Payment Confirmation; ACH Operations e Correspondent Bank Operations → Payment Rail; Direct Debit Collection e Direct Debits Service (provisórios) |
| Renomeados | Brokered Product → Brokered Product Proxy; Payment Rail Operations → Payment Rail; Payment Initiation → Payment Order Initiation; Credit Card Position Keeping → Card Transaction Tracking |
| Substituído | Product Broker Agreement → Partner Agreement |
| Functional Pattern alterado | Card Network Participant Facility e Fiduciary Agreement: Fulfill → Agree Terms. Consumer Advisory Services, Corporate Tax Advisory e Legal Advisory: Fulfill → Advise |
| BQ renomeado (amplo) | "Account Statement" → "Financial Status Tracking" e "Service Fee" → "Charge" nos SDs de conta, empréstimo e cartão |
| SDs sem Service Operation (sem API) | Brokered Product Proxy, Card Transaction Tracking, Claim Administration, Claim Assessment, Customer Surveys, Funeral Policy, Incentive Account, Incentive Program Directory, Issued Certificate Directory, Legal Advisory, Operations Log, Partner Administration, Partner Management, Rewards Delivery, Rewards Inventory, Rewards Menu |

## Reference Data (4 Business Domains, 33 Service Domains)

| Business Domain | Service Domains |
|---|---|
| External Agency | Contractor and Supplier Agreement, Correspondent Bank Directory, Correspondent Bank Relationship Management, Information Provider Administration, Interbank Relationship Management, Operations Log, Partner Administration, Partner Agreement, Partner Management, Product Service Agency, Service Provider Operations, Sub Custodian Agreement, Syndicate Management |
| Market Data | Counterparty Administration, Financial Instrument Reference Data Management, Financial Market Analysis, Financial Market Research, Information Provider Operation, Location Data Management, Market Data Switch Administration, Market Data Switch Operation, Market Information Management, Public Reference Data Management, Quant Model |
| Party | Legal Entity Directory, Party Routing Profile |
| Product Management | Discount Pricing, Product Deployment, Product Design, Product Directory, Product Quality Assurance, Product Training, Special Pricing Conditions |

## Sales and Service (6 Business Domains, 74 Service Domains)

| Business Domain | Service Domains |
|---|---|
| Channel Specific | ATM Network Management, ATM Network Operations, Advanced Voice Services Management, Advanced Voice Services Operations, Branch Currency Distribution, Branch Currency Management, Branch Location Management, Branch Location Operations, Branch Network Management, Card Terminal Administration, Card Terminal Operation, Contact Center Management, Contact Center Operations, Product Inventory Distribution, Product Inventory Item Management, eBranch Management, eBranch Operations |
| Cross Channel | Contact Handler, Contact Routing, Customer Workbench, Interactive Help, Party Authentication, Point of Service, Service Directory, Servicing Activity Analysis, Servicing Event History, Session Dialogue, Transaction Authorization |
| Customer Management | Account Recovery, Customer Access Entitlement, Customer Agreement, Customer Behavior Insights, Customer Consent, Customer Credit Rating, Customer Event History, Customer Financial Insights, Customer Product And Service Eligibility, Customer Product and Service Directory, Customer Proposition, Customer Relationship Management, Party Reference Data Directory, Payee Management, Sales Product Agreement |
| Marketing | Advertising, Brand Management, Business Development, Customer Campaign Design, Customer Campaign Management, Customer Surveys, Promotional Events, Prospect Campaign Design, Prospect Campaign Management |
| Sales | Commission Agreement, Commissions, Customer Campaign Execution, Customer Offer, Lead and Opportunity Management, Party Lifecycle Management, Product Expert Sales Support, Product Matching, Product Sales Support, Prospect Campaign Execution, Sales Planning, Sales Product, Underwriting |
| Servicing | Card Case, Case Root Cause Analysis, Customer Case, Customer Case Management, Loan Syndication, Payment Order Initiation, Servicing Issue, Servicing Mandate, Servicing Order |

## Operations and Execution (12 Business Domains, 130 Service Domains)

| Business Domain | Service Domains |
|---|---|
| Account Management *(Cross Product Operations)* | Account Reconciliation, Accounts Receivable, Counterparty Risk, Customer Position, Fraud Diagnosis, Fraud Evaluation, Position Keeping, Position Management, Product Combination, Reward Points Account, Securities Position Keeping, Transaction Engine |
| Collateral Administration *(Cross Product Operations)* | Collateral Allocation Management, Collateral Asset Administration, Collections, Party Asset Directory |
| Operational Services *(Cross Product Operations)* | Card Collections, Card Transaction Switch, Channel Activity Analysis, Channel Activity History, Customer Billing, Delinquent Account Handling, Disbursement, Incentive Account, Incentive Program Directory, Internal Bank Account, Issued Certificate Directory, Issued Device Administration, Issued Device Tracking, Leasing Item Administration, Open Item Management, Processing Order, Reward Points Awards And Redemption, Rewards Delivery, Rewards Inventory, Rewards Menu |
| Payments *(Cross Product Operations)* | Card Clearing, Card Financial Settlement, Card eCommerce Gateway, Central Cash Handling, Cheque Processing, Financial Gateway, Financial Message Analysis, Payment Confirmation, Payment Orchestration, Payment Rail, Payment Settlement |
| Cards *(Product Specific Fulfillment)* | Card Authorization, Card Network Participant Facility, Card Transaction Capture, Card Transaction Tracking, Corporate Card Service, Credit Card, Merchant Acquiring Facility, Merchant Relations |
| Consumer Services *(Product Specific Fulfillment)* | Bank Drafts, Brokered Product Proxy, Claim Administration, Claim Assessment, Consumer Advisory Services, Consumer Investments, Corporate Trust Services, Currency Exchange, Customer Tax Handling, Funeral Policy, Trust Services |
| Corporate Financing and Advisory Services *(Product Specific Fulfillment)* | Corporate Finance, Corporate Tax Advisory, Mergers and Acquisitions Advisory, Private Placement, Public Offering |
| Investment Management *(Product Specific Fulfillment)* | Investment Account, Investment Portfolio Analysis, Investment Portfolio Management, Investment Portfolio Planning, eTrading Workbench |
| Loans and Deposits *(Product Specific Fulfillment)* | Consumer Loan, Corporate Current Account, Corporate Lease, Corporate Loan, Current Account, Fiduciary Agreement, Leasing, Loan, Merchandising Loan, Mortgage Loan, Savings Account, Standing Order, Term Deposit, Term Deposit Framework Agreement, Virtual Account |
| Market Operations *(Product Specific Fulfillment)* | Corporate Action, Custody Administration, Financial Instrument Valuation, Hedge Fund Administration, Mutual Fund Administration, Order Allocation, Securities Fails Processing, Trade Clearing, Trade Confirmation Matching, Trade Settlement, Trade and Price Reporting, Unit Trust Administration |
| Trade Banking *(Product Specific Fulfillment)* | Bank Guarantee, Cash Concentration, Cash Management And Account Services, Cheque Lock Box, Corporate Payroll Services, Credit Facility, Credit Management, Direct Debit, Direct Debit Mandate, Factoring, Letter of Credit, Limit and Exposure Management, Notional Pooling, Project Finance, Syndicated Loan |
| Wholesale Trading *(Product Specific Fulfillment)* | Credit Risk Operations, Dealer Desk, ECM And DCM, Market Making, Market Order, Market Order Execution, Program Trading, Quote Management, Suitability Checking, Trader Position Operations, Trading Book Oversight, Trading Models |

## Risk and Compliance (4 Business Domains, 36 Service Domains)

| Business Domain | Service Domains |
|---|---|
| Bank Portfolio and Treasury | Asset And Liability Management, Asset Securitization, Bank Portfolio Administration, Bank Portfolio Analysis, Corporate Treasury, Corporate Treasury Analysis, Stock Lending and Repos |
| Business Analysis | Branch Portfolio, Channel Portfolio, Competitor Analysis, Contribution Analysis, Customer Portfolio, Market Analysis, Market Research, Product Portfolio, Segment Direction |
| Models | Business Risk Models, Contribution Models, Credit Risk Models, Credit and Margin Management, Customer Behavior Models, Economic Capital, Financial Instrument Valuation Models, Fraud Model, Gap Analysis, Liquidity Risk Models, Market Risk Models, Operational Risk Models, Production Risk Models |
| Regulations and Compliance | Compliance Reporting, Financial Accounting, Financial Statement Assessment, Fraud Resolution, Guideline Compliance, Regulatory Compliance, Regulatory Reporting |

## Business Support (10 Business Domains, 68 Service Domains)

| Business Domain | Service Domains |
|---|---|
| Buildings Equipment and Facilities | Building Maintenance, Equipment Administration, Equipment Maintenance, Property Portfolio, Site Administration, Site Operations, Utilities Administration |
| Business Command and Control | Business Unit Accounting, Business Unit Direction, Business Unit Financial Analysis, Business Unit Financial Operations, Business Unit Management, Organization Direction |
| Business Direction | Continuity Planning, Corporate Policies, Corporate Strategy, Enterprise Architecture, Products and Services Direction |
| Corporate Relations | Corporate Alliance and Stake Holder, Corporate Communications, Corporate Relationship, Investor Relations, Regulatory And Legal Authority |
| Document Management and Archive | Archive Services, Correspondence, Document Directory, Document Services |
| Finance | Enterprise Tax Administration, Financial Compliance, Financial Control, Financial Statements |
| Human Resource Management | Employee Access, Employee Assignment, Employee Benefits, Employee Certification, Employee Data Management, Employee Evaluation, Employee Payroll And Incentives, Employee and Contractor Contract, Human Resources Direction, Recruitment, Travel and Expenses, Workforce Training |
| IT Management | Development Environment, IT Standards And Guidelines, IT Systems Direction, Internal Network Operation, Operational Gateway, Platform Operations, Production Release, System Deployment, System Development, Systems Administration, Systems Assurance, Systems Help Desk, Systems Operations |
| Knowledge and Intellectual Property Management | Intellectual Property Portfolio, Knowledge Exchange, Management Manual |
| Non-IT and Non-HR Enterprise Services | Approved Supplier Directory, Company Billing and Payments, Fixed Asset Register, Internal Audit, Legal Advisory, Legal Compliance, Procurement, Security Advisory, Security Assurance |
