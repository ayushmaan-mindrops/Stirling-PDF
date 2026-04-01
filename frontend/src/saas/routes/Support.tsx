import { Anchor, Box, Container, List, Paper, Stack, Text, Title } from '@mantine/core';
import { Link } from 'react-router-dom';
import { getBaseUrl } from '@app/constants/app';
import { useDocumentMeta } from '@app/hooks/useDocumentMeta';

export default function Support() {
  const baseUrl = getBaseUrl();

  useDocumentMeta({
    title: 'Support - PaperBolt',
    description: 'PaperBolt support information for account access, billing, and PDF workflow help.',
    ogTitle: 'Support - PaperBolt',
    ogDescription: 'PaperBolt support information for account access, billing, and PDF workflow help.',
    ogImage: `${baseUrl}/og_images/home.png`,
    ogUrl: `${window.location.origin}${window.location.pathname}`,
  });

  return (
    <Box style={{ minHeight: '100vh', background: '#f8f9fa', padding: '2rem 1rem' }}>
      <Container size="md">
        <Paper radius="lg" p="xl" shadow="sm" withBorder>
          <Stack gap="lg">
            <Stack gap="xs">
              <Title order={1}>Support</Title>
              <Text c="dimmed">PaperBolt help and contact information</Text>
            </Stack>

            <Text>
              If you need help with account access, Marketplace subscription activation, billing,
              or PDF processing workflows, please contact the PaperBolt team.
            </Text>

            <Stack gap="xs">
              <Title order={3}>Common Support Topics</Title>
              <List spacing="xs">
                <List.Item>Sign-in and Microsoft account access issues</List.Item>
                <List.Item>Marketplace purchase or subscription activation problems</List.Item>
                <List.Item>PDF merge, split, compress, convert, and export questions</List.Item>
                <List.Item>Unexpected processing errors or file handling issues</List.Item>
              </List>
            </Stack>

            <Stack gap="xs">
              <Title order={3}>Contact</Title>
              <Text>
                Email: <Anchor href="mailto:support@paperbolt.app">support@paperbolt.app</Anchor>
              </Text>
              <Text>
                Please include your account email, subscription details if relevant, and a short
                description of the issue so we can help faster.
              </Text>
            </Stack>

            <Stack gap="xs">
              <Title order={3}>Related Information</Title>
              <Text>
                For details about how we handle account and subscription data, see the{' '}
                <Anchor component={Link} to="/privacy">Privacy Policy</Anchor>.
              </Text>
            </Stack>
          </Stack>
        </Paper>
      </Container>
    </Box>
  );
}
