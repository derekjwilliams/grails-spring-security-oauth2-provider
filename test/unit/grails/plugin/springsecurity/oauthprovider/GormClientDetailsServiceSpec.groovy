package grails.plugin.springsecurity.oauthprovider

import grails.plugin.springsecurity.SpringSecurityUtils
import grails.test.mixin.Mock
import grails.test.mixin.TestFor
import org.springframework.security.oauth2.provider.ClientDetails
import org.springframework.security.oauth2.provider.NoSuchClientException
import spock.lang.Specification
import spock.lang.Unroll

@TestFor(GormClientDetailsService)
@Mock([GormOAuth2Client])
class GormClientDetailsServiceSpec extends Specification {

    void setup() {
        service.grailsApplication = grailsApplication

        SpringSecurityUtils.securityConfig = [oauthProvider: [:]] as ConfigObject
        setUpDefaultClientConfig()

        setClientClassName('grails.plugin.springsecurity.oauthprovider.GormOAuth2Client')
    }

    private void setClientClassName(clientClassName) {
        def clientLookup = [
                className: clientClassName,
                clientIdPropertyName: 'clientId',
                clientSecretPropertyName: 'clientSecret',
                accessTokenValiditySecondsPropertyName: 'accessTokenValiditySeconds',
                refreshTokenValiditySecondsPropertyName: 'refreshTokenValiditySeconds',
                authoritiesPropertyName: 'authorities',
                authorizedGrantTypesPropertyName: 'authorizedGrantTypes',
                resourceIdsPropertyName: 'resourceIds',
                scopesPropertyName: 'scopes',
                redirectUrisPropertyName: 'redirectUris'
        ]
        SpringSecurityUtils.securityConfig.oauthProvider.clientLookup = clientLookup
    }

    private void setUpDefaultClientConfig(Map overrides = [:]) {
        def clientConfig = [
                resourceIds: [],
                authorizedGrantTypes: [],
                scope: [],
                registeredRedirectUri: null,
                authorities: [],
                accessTokenValiditySeconds: null,
                refreshTokenValiditySeconds: null
        ] << overrides
        SpringSecurityUtils.securityConfig.oauthProvider.defaultClientConfig = clientConfig
    }

    private def getClientLookup() {
        return SpringSecurityUtils.securityConfig.oauthProvider.clientLookup
    }

    private def getDefaultClientConfig() {
        return SpringSecurityUtils.securityConfig.oauthProvider.defaultClientConfig
    }

    void "request valid client using dynamic look up"() {
        given:
        new GormOAuth2Client(
                clientId: 'gormClient',
                clientSecret: 'grails',
                accessTokenValiditySeconds: 1234,
                refreshTokenValiditySeconds: 5678,
                authorities: ['ROLE_CLIENT'] as Set,
                authorizedGrantTypes: ['implicit'] as Set,
                resourceIds: ['someResource'] as Set,
                scopes: ['kaleidoscope'] as Set,
                redirectUris: ['http://anywhereButHere'] as Set
        ).save()

        when:
        def details = service.loadClientByClientId('gormClient')

        then:
        details instanceof ClientDetails

        and:
        details.clientId == 'gormClient'
        details.clientSecret == 'grails'

        and:
        details.accessTokenValiditySeconds == 1234
        details.refreshTokenValiditySeconds == 5678

        and:
        details.authorities.size() == 1
        details.authorities.find { it.authority == 'ROLE_CLIENT' }

        and:
        details.authorizedGrantTypes.size() == 1
        details.authorizedGrantTypes.contains('implicit')

        and:
        details.resourceIds.size() == 1
        details.resourceIds.contains('someResource')

        and:
        details.scope.size() == 1
        details.scope.contains('kaleidoscope')

        and:
        details.registeredRedirectUri.size() == 1
        details.registeredRedirectUri.contains('http://anywhereButHere')
    }

    void "requested client not found"() {
        when:
        service.loadClientByClientId('gormClient')

        then:
        def e = thrown(NoSuchClientException)
        e.message == 'No client with requested id: gormClient'
    }

    void "invalid client domain class name [#className]"() {
        given:
        setClientClassName(className)

        when:
        service.loadClientByClientId('gormClient')

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The specified client domain class '$className' is not a domain class"

        where:
        _   |   className
        _   |   'invalidClientClass'
        _   |   null
    }

    void "client secret can be optional"() {
        given:
        def client = new GormOAuth2Client()

        when:
        def details = service.createClientDetails(client, clientLookup, defaultClientConfig)

        then:
        !details.isSecretRequired()
    }

    @Unroll
    void "[#type] token validity can be null -- honor default if not specified"() {
        given:
        setUpDefaultClientConfig([(name): 13490])

        and:
        def client = new GormOAuth2Client()

        when:
        def details = service.createClientDetails(client, clientLookup, defaultClientConfig)

        then:
        details."$detailsMethodName"() == 13490

        where:
        type        |   name                            |   detailsMethodName
        'access'    |   'accessTokenValiditySeconds'    |   'getAccessTokenValiditySeconds'
        'refresh'   |   'refreshTokenValiditySeconds'   |   'getRefreshTokenValiditySeconds'
    }

    void "scopes can be optional -- honor default if not specified"() {
        given:
        setUpDefaultClientConfig([scope: ['read']])

        and:
        def client = new GormOAuth2Client(scopes: null)

        when:
        def details = service.createClientDetails(client, clientLookup, defaultClientConfig)

        then:
        details.scoped
        details.scope.size() == 1
        details.scope.contains('read')
    }

    void "multiple scopes"() {
        given:
        def client = new GormOAuth2Client(scopes: ['read', 'write', 'trust'] as Set)

        when:
        def details = service.createClientDetails(client, clientLookup, defaultClientConfig)

        then:
        details.scoped
        details.scope.size() == 3
        details.scope.contains('read')
        details.scope.contains('write')
        details.scope.contains('trust')
    }

    @Unroll
    void "authorities default to nothing"() {
        given:
        def client = new GormOAuth2Client()

        when:
        def details = service.createClientDetails(client, clientLookup, defaultClientConfig)

        then:
        details.authorities.empty
    }

    void "multiple authorities"() {
        given:
        def client = new GormOAuth2Client(authorities: ['ROLE_CLIENT', 'ROLE_TRUSTED_CLIENT'] as Set)

        when:
        def details = service.createClientDetails(client, clientLookup, defaultClientConfig)

        then:
        details.authorities.size() == 2
        details.authorities.find { it.authority == 'ROLE_CLIENT' }
        details.authorities.find { it.authority == 'ROLE_TRUSTED_CLIENT' }
    }

    void "no grant types specified for client or in default config"() {
        given:
        setUpDefaultClientConfig([authorizedGrantTypes: []])

        and:
        def client = new GormOAuth2Client(authorizedGrantTypes: null)

        when:
        def details = service.createClientDetails(client, clientLookup, defaultClientConfig)

        then:
        details.authorizedGrantTypes.size() == 0
    }

    void "grant types not required -- honor default if not specified"() {
        given:
        setUpDefaultClientConfig([authorizedGrantTypes: ['foo', 'bar']])

        and:
        def client = new GormOAuth2Client(authorizedGrantTypes: null)

        when:
        def details = service.createClientDetails(client, clientLookup, defaultClientConfig)

        then:
        details.authorizedGrantTypes.size() == 2
        details.authorizedGrantTypes.contains('foo')
        details.authorizedGrantTypes.contains('bar')
    }

    void "multiple grant types"() {
        given:
        def client = new GormOAuth2Client(authorizedGrantTypes: ['password','authorization_code', 'refresh_token', 'implicit'] as Set)

        when:
        def details = service.createClientDetails(client, clientLookup, defaultClientConfig)

        then:
        details.authorizedGrantTypes.size() == 4
        details.authorizedGrantTypes.contains('password')
        details.authorizedGrantTypes.contains('authorization_code')
        details.authorizedGrantTypes.contains('refresh_token')
        details.authorizedGrantTypes.contains('implicit')
    }

    void "redirect uris are not required -- honor default if not specified"() {
        given:
        setUpDefaultClientConfig([registeredRedirectUri: ['http://somewhere.com']])

        and:
        def client = new GormOAuth2Client(redirectUris: null)

        when:
        def details = service.createClientDetails(client, clientLookup, defaultClientConfig)

        then:
        details.registeredRedirectUri.size() == 1
        details.registeredRedirectUri.contains('http://somewhere.com')
    }

    void "multiple redirect uris"() {
        given:
        def client = new GormOAuth2Client(redirectUris: ['http://somewhere', 'http://nowhere'] as Set)

        when:
        def details = service.createClientDetails(client, clientLookup, defaultClientConfig)

        then:
        details.registeredRedirectUri.size() == 2
        details.registeredRedirectUri.contains('http://somewhere')
        details.registeredRedirectUri.contains('http://nowhere')
    }

    void "resource ids are optional -- honor default if not specified"() {
        given:
        setUpDefaultClientConfig([resourceIds: ['someResource']])

        and:
        def client = new GormOAuth2Client(resourceIds: null)

        when:
        def details = service.createClientDetails(client, clientLookup, defaultClientConfig)

        then:
        details.resourceIds.size() == 1
        details.resourceIds.contains('someResource')
    }
}
