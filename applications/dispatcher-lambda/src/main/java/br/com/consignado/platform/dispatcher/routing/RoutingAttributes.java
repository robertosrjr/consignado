package br.com.consignado.platform.dispatcher.routing;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;

/** Atributos em claro do item do inbox usados para rotear (nao inclui CPF nem payload). */
public record RoutingAttributes(
    TenantId tenant, ConvenioType convenio, boolean portability, Money amount) {}
