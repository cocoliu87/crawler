import Head from "next/head";
import { Fragment } from "react";
import { Layout as DashboardLayout } from "src/layouts/dashboard/layout";
import {
  Box,
  Container,
  Stack,
  Typography,
  Card,
  InputAdornment,
  OutlinedInput,
  SvgIcon,
} from "@mui/material";
import MagnifyingGlassIcon from "@heroicons/react/24/solid/MagnifyingGlassIcon";
import { useState } from "react";

const Page = () => {
  const [searchString, setSearchString] = useState("");
  const searchInputChangeHandler = (event) => {
    console.log("search string input: ", event.target.value);
    const input = event.target.value;
    setSearchString(input);
  };
  return (
    <Fragment>
      <Head>
        <title>Search | Devias Kit</title>
      </Head>
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          py: 8,
        }}
      >
        <Container maxWidth="xl">
          <Stack spacing={3}>
            <Stack direction="row" justifyContent="space-between" spacing={4}>
              <Stack spacing={1}>
                <Typography variant="h4">Search</Typography>
              </Stack>
            </Stack>
            <Card sx={{ p: 2 }}>
              <OutlinedInput
                defaultValue=""
                fullWidth
                placeholder="Type to search information from crawler"
                onChange={searchInputChangeHandler}
                startAdornment={
                  <InputAdornment position="start">
                    <SvgIcon color="action" fontSize="small">
                      <MagnifyingGlassIcon />
                    </SvgIcon>
                  </InputAdornment>
                }
                sx={{ maxWidth: 500 }}
              />
            </Card>
          </Stack>
        </Container>
      </Box>
    </Fragment>
  );
};

Page.getLayout = (page) => <DashboardLayout>{page}</DashboardLayout>;

export default Page;
