//______________________________________________________________________________
//
// $Id: SRMReserveSpaceClientV2.java 10382 2008-10-16 15:33:29Z litvinse $
// $Author: litvinse $
//
//
// created 08/06 by Dmitry Litvintsev (litvinse@fnal.gov)
//
//______________________________________________________________________________

/*
COPYRIGHT STATUS:
  Dec 1st 2001, Fermi National Accelerator Laboratory (FNAL) documents and
  software are sponsored by the U.S. Department of Energy under Contract No.
  DE-AC02-76CH03000. Therefore, the U.S. Government retains a  world-wide
  non-exclusive, royalty-free license to publish or reproduce these documents
  and software for U.S. Government purposes.  All documents and software
  available from this server are protected under the U.S. and Foreign
  Copyright Laws, and FNAL reserves all rights.
 
 
 Distribution of the software available from this server is free of
 charge subject to the user following the terms of the Fermitools
 Software Legal Information.
 
 Redistribution and/or modification of the software shall be accompanied
 by the Fermitools Software Legal Information  (including the copyright
 notice).
 
 The user is asked to feed back problems, benefits, and/or suggestions
 about the software to the Fermilab Software Providers.
 
 
 Neither the name of Fermilab, the  URA, nor the names of the contributors
 may be used to endorse or promote products derived from this software
 without specific prior written permission.
 
 
 
  DISCLAIMER OF LIABILITY (BSD):
 
  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
  "AS IS" AND ANY EXPRESS OR IMPLIED  WARRANTIES, INCLUDING, BUT NOT
  LIMITED TO, THE IMPLIED  WARRANTIES OF MERCHANTABILITY AND FITNESS
  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL FERMILAB,
  OR THE URA, OR THE U.S. DEPARTMENT of ENERGY, OR CONTRIBUTORS BE LIABLE
  FOR  ANY  DIRECT, INDIRECT,  INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
  OF SUBSTITUTE  GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY  OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT  OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE  POSSIBILITY OF SUCH DAMAGE.
 
 
  Liabilities of the Government:
 
  This software is provided by URA, independent from its Prime Contract
  with the U.S. Department of Energy. URA is acting independently from
  the Government and in its own private capacity and is not acting on
  behalf of the U.S. Government, nor as its contractor nor its agent.
  Correspondingly, it is understood and agreed that the U.S. Government
  has no connection to this software and in no manner whatsoever shall
  be liable for nor assume any responsibility or obligation for any claim,
  cost, or damages arising out of or resulting from the use of the software
  available from this server.
 
 
  Export Control:
 
  All documents and software available from this server are subject to U.S.
  export control laws.  Anyone downloading information from this server is
  obligated to secure any necessary Government licenses before exporting
  documents or software obtained from this server.
 */

/*
 * SrmReserveSpaceClientV2.java
 *
 * Created on January 28, 2003, 2:54 PM
 */

package gov.fnal.srm.util;
import java.util.HashMap;
import java.util.Iterator;
import org.globus.util.GlobusURL;
import diskCacheV111.srm.FileMetaData;
import diskCacheV111.srm.RequestFileStatus;
import diskCacheV111.srm.RequestStatus;
import org.dcache.srm.v2_2.ISRM;
import org.dcache.srm.client.SRMClientV2;
import org.ietf.jgss.GSSCredential;
import java.io.IOException;
import java.text.DateFormat;
import org.apache.axis.types.URI;
import org.dcache.srm.v2_2.*;
import org.dcache.srm.util.RequestStatusTool;

public class SRMReserveSpaceClientV2 extends SRMClient implements Runnable {
    private GlobusURL srmURL;
    SrmReserveSpaceRequest request = new SrmReserveSpaceRequest();
    private org.ietf.jgss.GSSCredential credential = null;
    private ISRM srmv2;
    private Thread hook;
    private String requestToken;
    
    public SRMReserveSpaceClientV2(Configuration configuration,
        GlobusURL url) {
        super(configuration);
        srmURL=url;
        try {
            credential = getGssCredential();
        } catch (Exception e) {
            credential = null;
            System.err.println("Couldn't getGssCredential.");
        }
    }
    
    public void connect() throws Exception {
        
        srmv2 = new SRMClientV2(srmURL,
            getGssCredential(),
            configuration.getRetry_timeout(),
            configuration.getRetry_num(),
            configuration.getLogger(),
            doDelegation,
            fullDelegation,
            gss_expected_name,
            configuration.getWebservice_path());
    }
    
    public void start() throws Exception {
        try {
            if (credential.getRemainingLifetime() < 60)
                throw new Exception(
                    "Remaining lifetime of credential is less than a minute.");
        } catch (org.ietf.jgss.GSSException gsse) {
            throw gsse;
        }
        try {
            
            TRetentionPolicy rp   =  null;
            if(configuration.getRetentionPolicy() != null ) {
                rp = TRetentionPolicy.fromString(configuration.getRetentionPolicy());
            }
            TAccessLatency   al = null;
            if(configuration.getAccessLatency() != null ) {
                al = TAccessLatency.fromString(configuration.getAccessLatency());
            }
            TRetentionPolicyInfo rpi = new TRetentionPolicyInfo(rp,al);
            request.setRetentionPolicyInfo(rpi);
            if (configuration.getDesiredReserveSpaceSize()!=null) { 
                    request.setDesiredSizeOfTotalSpace(new org.apache.axis.types.UnsignedLong(configuration.getDesiredReserveSpaceSize().longValue()));
            }
            if (configuration.getGuaranteedReserveSpaceSize()!=null){ 
                    request.setDesiredSizeOfGuaranteedSpace(new org.apache.axis.types.UnsignedLong(configuration.getGuaranteedReserveSpaceSize().longValue()));
            }
            request.setUserSpaceTokenDescription(configuration.getSpaceTokenDescription());
            if (configuration.getDesiredLifetime()!=null) { 
                    request.setDesiredLifetimeOfReservedSpace(new Integer((int)(configuration.getDesiredLifetime().longValue())));
            }
            if (configuration.getArrayOfClientNetworks()!=null ||
                configuration.getConnectionType()!=null ||
                configuration.getAccessPattern()!=null ||
                configuration.getProtocols()!=null) {
                    TTransferParameters tp = new TTransferParameters();
                    if (configuration.getArrayOfClientNetworks()!=null) { 
                            tp.setArrayOfClientNetworks(new ArrayOfString(configuration.getArrayOfClientNetworks()));
                    }
                    if (configuration.getConnectionType()!=null) { 
                            tp.setConnectionType(TConnectionType.fromString(configuration.getConnectionType()));
                    }
                    if (configuration.getAccessPattern()!=null) {
                            tp.setAccessPattern(TAccessPattern.fromString(configuration.getAccessPattern()));
                    }
                    if (configuration.getProtocols()!=null) { 
                            tp.setArrayOfTransferProtocols(new ArrayOfString(configuration.getProtocols()));
                    }
                    request.setTransferParameters(tp);
            }
            hook = new Thread(this);
            Runtime.getRuntime().addShutdownHook(hook);
            
            SrmReserveSpaceResponse response = srmv2.srmReserveSpace(request);

            if ( response == null ) {
                throw new IOException(" null SrmReserveSpace");
            }
            
            TReturnStatus rs     = response.getReturnStatus();
            requestToken         = response.getRequestToken();
            dsay(" srm returned requestToken = "+requestToken);
            if ( rs == null) {
                throw new IOException(" null TReturnStatus ");
            }
            if (RequestStatusTool.isFailedRequestStatus(rs)) {
                throw new IOException("srmReserveSpace submission failed, unexpected or failed return status : "+
                    rs.getStatusCode()+" explanation="+rs.getExplanation());
            }
	    if (response.getSpaceToken()!=null) { 
                    System.out.println("Space token ="+
				       response.getSpaceToken());
                    logger.log("lifetime = "+
			       response.getLifetimeOfReservedSpace());
		    if (response.getRetentionPolicyInfo()!=null) { 
			    logger.log("access latency = "+
				       response.getRetentionPolicyInfo().getAccessLatency());
			    logger.log("retention policy = "+
				       response.getRetentionPolicyInfo().getRetentionPolicy());
		    }
                    logger.log("guaranteed size = "+
			       response.getSizeOfGuaranteedReservedSpace());
                    logger.log("total size = "+
			       response.getSizeOfTotalReservedSpace());
	    }
            else { 
		    while(true) {
			    long estimatedWaitInSeconds = 5;
			    
			    if(estimatedWaitInSeconds > 60) {
				    estimatedWaitInSeconds = 60;
			    }
			    try {
				    say("sleeping "+estimatedWaitInSeconds+" seconds ...");
				    Thread.sleep(estimatedWaitInSeconds * 1000);
			    } catch(InterruptedException ie) {
				    System.out.println("Interrupted, quitting");
				    if ( requestToken != null  ) {
					    abortRequest();
				    }
				    System.exit(1);
			    }
			    //
			    // check our request
			    //
			    SrmStatusOfReserveSpaceRequestRequest req = new SrmStatusOfReserveSpaceRequestRequest();
			    req.setRequestToken(requestToken);
			    req.setAuthorizationID(request.getAuthorizationID());
			    SrmStatusOfReserveSpaceRequestResponse statusOfReserveSpaceRequestResponse =  srmv2.srmStatusOfReserveSpaceRequest(req);
			    
			    if( statusOfReserveSpaceRequestResponse == null) {
				    throw new IOException(" null statusOfReserveSpaceRequestResponse");
			    }
			    TReturnStatus status = statusOfReserveSpaceRequestResponse.getReturnStatus();
			    if ( status == null ) {
				    throw new IOException(" null return status");
			    }
			    if ( status.getStatusCode() == null ) {
				    throw new IOException(" null status code");
			    }
			    if (RequestStatusTool.isFailedRequestStatus(status)){
				    logger.log("status: code="+status.getStatusCode()+
					       " explanantion="+status.getExplanation());
				    throw new IOException("SrmStatusOfReserveSpaceRequest unexpected or failed status : "+
							  status.getStatusCode() +" explanation="+status.getExplanation());
			    }
			    if (status.getStatusCode()==TStatusCode.SRM_SUCCESS ||
				status.getStatusCode()==TStatusCode.SRM_SPACE_AVAILABLE ||
				status.getStatusCode()==TStatusCode.SRM_LOWER_SPACE_GRANTED) {
				    System.out.println("Space token ="+
						       statusOfReserveSpaceRequestResponse.getSpaceToken());
				    logger.log("lifetime = "+
					       statusOfReserveSpaceRequestResponse.getLifetimeOfReservedSpace());
				    logger.log("access latency = "+
					       statusOfReserveSpaceRequestResponse.getRetentionPolicyInfo().getAccessLatency());
				    logger.log("retention policy = "+
					       statusOfReserveSpaceRequestResponse.getRetentionPolicyInfo().getRetentionPolicy());
				    logger.log("guaranteed size = "+
					       statusOfReserveSpaceRequestResponse.getSizeOfGuaranteedReservedSpace());
				    logger.log("total size = "+
					       statusOfReserveSpaceRequestResponse.getSizeOfTotalReservedSpace());
				    break;
			    }
			    if(statusOfReserveSpaceRequestResponse.getEstimatedProcessingTime() != null &&
			       statusOfReserveSpaceRequestResponse.getEstimatedProcessingTime().intValue() < estimatedWaitInSeconds &&
			       statusOfReserveSpaceRequestResponse.getEstimatedProcessingTime().intValue() >=1) {
				    estimatedWaitInSeconds = statusOfReserveSpaceRequestResponse.getEstimatedProcessingTime().intValue();
			    }
		    }
	    }
            Runtime.getRuntime().removeShutdownHook(hook);
        } 
	catch(Exception e) {
		esay(e.toString());
		try {
			if ( requestToken != null ) {
				abortRequest();
			}
			System.exit(1);
		} catch (Exception e1) {
			logger.elog(e1);
			System.exit(1);
		}
        }
    }

    
    public void run() {
        try {
            say("stopping ");
            if ( requestToken != null ) {
                abortRequest();
            }
        } catch(Exception e) {
            logger.elog(e);
        }
    }
    
    public void abortRequest() throws Exception {
        SrmAbortRequestRequest abortRequest = new SrmAbortRequestRequest();
        abortRequest.setRequestToken(requestToken);
        SrmAbortRequestResponse abortResponse = srmv2.srmAbortRequest(abortRequest);
        if (abortResponse == null) {
            logger.elog(" SrmAbort is null");
        } else {
            TReturnStatus returnStatus = abortResponse.getReturnStatus();
            if(returnStatus == null) {
                esay("srmAbort return status is null");
                return;
            }
            say("srmAbortRequest status code="+returnStatus.getStatusCode());
        }
    }
}
