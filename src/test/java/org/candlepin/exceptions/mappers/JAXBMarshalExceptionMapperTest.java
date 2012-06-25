/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.exceptions.mappers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jboss.resteasy.plugins.providers.jaxb.JAXBMarshalException;
import org.junit.Test;

import javax.ws.rs.core.Response;

/**
 * JAXBMarshalExceptionMapperTest
 */
public class JAXBMarshalExceptionMapperTest extends TestExceptionMapperBase {

    @Test
    public void handleExceptionWithoutResponse() {
        JAXBMarshalException nfe = new JAXBMarshalException("unacceptable");
        JAXBMarshalExceptionMapper nfem =
            injector.getInstance(JAXBMarshalExceptionMapper.class);
        Response r = nfem.toResponse(nfe);
        assertEquals(500, r.getStatus());
        verifyMessage(r, rtmsg("unacceptable"));
    }

    @Test
    public void handleExceptionWithResponse() {
        Response mockr = mock(Response.class);
        when(mockr.getStatus()).thenReturn(500);
        JAXBMarshalException nfe = new JAXBMarshalException("unacceptable", mockr);
        JAXBMarshalExceptionMapper nfem =
            injector.getInstance(JAXBMarshalExceptionMapper.class);
        Response r = nfem.toResponse(nfe);
        assertEquals(500, r.getStatus());
        verifyMessage(r, rtmsg("unacceptable"));
    }

    @Override
    public Class getMapperClass() {
        return JAXBMarshalExceptionMapper.class;
    }
}
