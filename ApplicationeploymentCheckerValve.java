import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class ApplicationeploymentCheckerValve extends ValveBase {

    private static final Log LOG = LogFactory.getLog(ApplicationeploymentCheckerValve.class);
    private static final Set<String> CATALINA_APPS = Set.of("my_app_1", "my_app_2");
    private ObjectName oNameCatalinaApps;

    public ApplicationeploymentCheckerValve() {

        super(true);

        try {
            oNameCatalinaApps = new ObjectName("Catalina:type=Deployer," + "host=localhost");
        } catch (MalformedObjectNameException e) {
            LOG.error("Unable to initialize object name.", e);
        }
    }

    @Override public void invoke(Request request, Response response) throws IOException, ServletException {

        if (!appsDeploymentComplete()) {
            LOG.error("Rejecting the request method " + request.getMethod() + " on URI " + request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            Writer writer = response.getWriter();
            writer.write("The requested resource [" + request.getRequestURI() + "] is not available. Please try again" +
                             " later.");
            response.finishResponse();
            return;
        }
        this.getNext().invoke(request, response);
    }

    private boolean appsDeploymentComplete() {

        MBeanServer mBeanServer = null;

        final List<MBeanServer> mBeanServers = MBeanServerFactory.findMBeanServer(null);

        if (!mBeanServers.isEmpty()) {
            mBeanServer = mBeanServers.get(0);
        }

        if (mBeanServer != null) {
            try {
                for (String app : CATALINA_APPS) {

                    boolean isDeploymentComplete = (Boolean) mBeanServer.invoke(oNameCatalinaApps, "isDeployed",
                                                                                new Object[] {app},
                                                                                new String[] {String.class.getName()});
                    if (!isDeploymentComplete) {
                        LOG.error("Deployment in-progress for application " + app);
                        return false;
                    }
                    LOG.debug("Deployment complete for application " + app);
                }
            } catch (Exception e) {
			    // You can throw exception and return 503 in this case as well
                LOG.error("Unable to get the application deployment status. Ignore this exception and assume " +
                              "application deployment is complete.", e);
            }
        }
        return true;
    }
}
