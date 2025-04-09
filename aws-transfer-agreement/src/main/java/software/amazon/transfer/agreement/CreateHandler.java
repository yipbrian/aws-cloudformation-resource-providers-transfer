package software.amazon.transfer.agreement;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import software.amazon.awssdk.services.transfer.TransferClient;
import software.amazon.awssdk.services.transfer.model.CreateAgreementRequest;
import software.amazon.awssdk.services.transfer.model.CreateAgreementResponse;
import software.amazon.awssdk.services.transfer.model.InternalServiceErrorException;
import software.amazon.awssdk.services.transfer.model.InvalidRequestException;
import software.amazon.awssdk.services.transfer.model.ResourceExistsException;
import software.amazon.awssdk.services.transfer.model.ThrottlingException;
import software.amazon.awssdk.services.transfer.model.TransferException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import com.amazonaws.util.CollectionUtils;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class CreateHandler extends BaseHandlerStd {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<TransferClient> proxyClient,
            final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();

        Map<String, String> allTags = new HashMap<>();
        if (request.getDesiredResourceTags() != null) {
            allTags.putAll(request.getDesiredResourceTags());
        }
        if (request.getSystemTags() != null) {
            allTags.putAll(request.getSystemTags());
        }
        model.setTags(Converter.TagConverter.translateTagfromMap(allTags));

        CreateAgreementRequest createAgreementRequest = CreateAgreementRequest.builder()
                .description(model.getDescription())
                .serverId(model.getServerId())
                .localProfileId(model.getLocalProfileId())
                .partnerProfileId(model.getPartnerProfileId())
                .baseDirectory(model.getBaseDirectory())
                .accessRole(model.getAccessRole())
                .status(model.getStatus())
                .preserveFilename(model.getPreserveFilename())
                .enforceMessageSigning(model.getEnforceMessageSigning())
                .customDirectories(Converter.CustomDirectoriesConverter.toSdk(model.getCustomDirectories()))
                .tags(
                        (CollectionUtils.isNullOrEmpty(model.getTags()))
                                ? null
                                : model.getTags().stream()
                                        .map(Converter.TagConverter::toSdk)
                                        .collect(Collectors.toList()))
                .build();

        try (TransferClient client = proxyClient.client()) {
            CreateAgreementResponse response =
                    proxy.injectCredentialsAndInvokeV2(createAgreementRequest, client::createAgreement);
            model.setAgreementId(response.agreementId());
            logger.log(String.format("%s created successfully", ResourceModel.TYPE_NAME));
        } catch (InvalidRequestException e) {
            throw new CfnInvalidRequestException(e.getMessage() + " " + createAgreementRequest.toString(), e);
        } catch (InternalServiceErrorException e) {
            throw new CfnServiceInternalErrorException("createAgreement", e);
        } catch (ResourceExistsException e) {
            throw new CfnAlreadyExistsException(
                    ResourceModel.TYPE_NAME,
                    model.getServerId() + "," + model.getLocalProfileId() + "," + model.getPartnerProfileId(),
                    e);
        } catch (ThrottlingException e) {
            throw new CfnThrottlingException("createAgreement", e);
        } catch (TransferException e) {
            throw new CfnGeneralServiceException(e.getMessage(), e);
        }

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(model)
                .status(OperationStatus.SUCCESS)
                .build();
    }
}
