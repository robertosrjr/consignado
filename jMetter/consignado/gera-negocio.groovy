// Uma thread; N propostas com valor fixo, seguidas de replay do mesmo payload/chave.
String tenant = 'banco-001'
vars.put('tenant', tenant)
vars.put('token', props.getProperty('token_' + tenant))
vars.put('key', UUID.randomUUID().toString())
vars.put('cpf', '52998224725') // fixture de teste; nao identifica cliente do ambiente
vars.put('amount', new BigDecimal(props.getProperty('business_amount', '1000.00')).setScale(2).toPlainString())
vars.put('convenio', props.getProperty('business_convenio', 'INSS'))
vars.put('portability', props.getProperty('business_portability', 'false'))
vars.put('installments', '84')
vars.put('expected_status', 'RECEIVED')
vars.put('business_case', 'true')
