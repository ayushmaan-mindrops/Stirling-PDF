import { Anchor, Box, Container, Paper, Stack, Text, Title } from '@mantine/core';
import { Link } from 'react-router-dom';
import { getBaseUrl } from '@app/constants/app';
import { useDocumentMeta } from '@app/hooks/useDocumentMeta';

export default function PrivacyPolicy() {
  const baseUrl = getBaseUrl();

  useDocumentMeta({
    title: 'Privacy Policy - PaperBolt',
    description: 'PaperBolt privacy policy for PDF processing, authentication, billing, and support.',
    ogTitle: 'Privacy Policy - PaperBolt',
    ogDescription: 'PaperBolt privacy policy for PDF processing, authentication, billing, and support.',
    ogImage: `${baseUrl}/og_images/home.png`,
    ogUrl: `${window.location.origin}${window.location.pathname}`,
  });

  return (
    <Box style={{ minHeight: '100vh', background: '#f8f9fa', padding: '2rem 1rem' }}>
      <Container size="md">
        <Paper radius="lg" p="xl" shadow="sm" withBorder>
          <Stack gap="lg">
            <Stack gap="xs">
              <Title order={1}>Privacy Policy</Title>
              <Text c="dimmed">Last updated: April 1, 2026</Text>
            </Stack>

            <Text>
              PaperBolt provides PDF processing and document workflow tools. This Privacy Policy
              explains what information we collect, how we use it, and the choices available to
              users of the service.
            </Text>

            <Stack gap="xs">
              <Title order={3}>Information We Collect</Title>
              <Text>
                We may collect account information such as your name, email address, authentication
                provider details, subscription details, billing-related identifiers, and service
                usage information needed to operate and secure the application.
              </Text>
              <Text>
                When you use PaperBolt PDF tools, files and processing metadata may be handled to
                provide requested features such as merge, split, compress, convert, and preview
                workflows.
              </Text>
            </Stack>

            <Stack gap="xs">
              <Title order={3}>How We Use Information</Title>
              <Text>
                We use information to authenticate users, provision access, process subscriptions,
                deliver PDF functionality, monitor service health, prevent abuse, and provide user
                support.
              </Text>
            </Stack>

            <Stack gap="xs">
              <Title order={3}>Subscription and Marketplace Data</Title>
              <Text>
                If you access PaperBolt through Microsoft Marketplace, we may process purchaser and
                subscription information received from Microsoft, including subscription ID, plan,
                quantity, and account identifiers required to provision and maintain access.
              </Text>
            </Stack>

            <Stack gap="xs">
              <Title order={3}>Data Sharing</Title>
              <Text>
                We do not sell personal information. Information may be shared with service
                providers and infrastructure platforms only as required to operate, secure, bill,
                and support the service.
              </Text>
            </Stack>

            <Stack gap="xs">
              <Title order={3}>Data Retention</Title>
              <Text>
                We retain information only for as long as necessary to provide the service, comply
                with legal obligations, resolve disputes, and enforce agreements.
              </Text>
            </Stack>

            <Stack gap="xs">
              <Title order={3}>Security</Title>
              <Text>
                We use reasonable administrative, technical, and organizational safeguards to help
                protect account, subscription, and document-processing data.
              </Text>
            </Stack>

            <Stack gap="xs">
              <Title order={3}>Contact</Title>
              <Text>
                For privacy-related questions or requests, please visit the <Anchor component={Link} to="/support">support page</Anchor>.
              </Text>
            </Stack>
          </Stack>
        </Paper>
      </Container>
    </Box>
  );
}
